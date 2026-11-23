package pl.syntaxdevteam.logincorex.auth.premium

import pl.syntaxdevteam.logincorex.auth.session.*
import pl.syntaxdevteam.logincorex.domain.account.*
import pl.syntaxdevteam.logincorex.domain.session.*
import pl.syntaxdevteam.logincorex.security.audit.SecurityAuditSink
import pl.syntaxdevteam.logincorex.storage.*
import java.lang.reflect.Proxy
import java.net.InetAddress
import java.time.*
import java.util.UUID
import java.util.concurrent.*
import kotlin.test.*

class VerifiedMojangAuthenticationLifecycleTest {
    @Test fun `premium admission denial does not activate or emit success audit`() = exercise("deny")
    @Test fun `premium admission failure remains fail closed`() = exercise("failure")
    @Test fun `disconnect during premium admission prevents late activation`() = exercise("disconnect")
    @Test fun `a completed premium check cannot activate a replacement connection`() = exercise("reconnect")

    @Test fun `null Java storage result remains fail closed`() = exercise("null")

    @Test fun `verified identity without opt-in still requires a password`() = exercisePreference(false)
    @Test fun `verified identity with opt-in activates using Mojang`() = exercisePreference(true)
    @Test fun `unavailable preference lookup does not activate a session`() = exercisePreference(null)

    private fun exercisePreference(enabled: Boolean?) {
        val now = Instant.parse("2026-10-10T00:00:00Z")
        val name = AccountUsername.parse("VerifiedRace")
        val account = AuthAccount(AccountId.random(), name, IdentityType.MOJANG,
            UUID.randomUUID(), AccountState.REGISTERED, now, now)
        val context = VerifiedMojangAuthenticationContext(
            ConnectionId.random(), name, InetAddress.getLoopbackAddress(), account.minecraftUuid,
        )
        val sessions = InMemorySessionRegistry()
        sessions.create(AuthSession.connecting(context.connectionId, name, context.sourceAddress, now))
        sessions.enterPreAuth(context.connectionId)
        val storage = Proxy.newProxyInstance(AccountStorage::class.java.classLoader, arrayOf(AccountStorage::class.java)) { _, method, args ->
            when {
                method.name == "bindVerifiedMojangIdentity" ->
                    CompletableFuture.completedFuture(MojangIdentityBindingResult.Bound(account, false))
                method.name.startsWith("isPremiumLoginEnabled") -> {
                    assertEquals(account.id.value, args[0])
                    assertEquals(account.minecraftUuid, args[1])
                    if (enabled != null) CompletableFuture.completedFuture(enabled)
                    else CompletableFuture.failedFuture<Boolean>(IllegalStateException("Storage unavailable"))
                }
                else -> error("Unexpected storage call ${method.name}")
            }
        } as AccountStorage
        val service = VerifiedMojangAuthenticationService(storage, sessions, SecurityAuditSink {
            CompletableFuture.completedFuture(null)
        }, Clock.fixed(now, ZoneOffset.UTC))
        val result = service.authenticate(context).toCompletableFuture()
        when (enabled) {
            true -> {
                assertIs<VerifiedMojangAuthenticationResult.Success>(result.get(2, TimeUnit.SECONDS))
                assertEquals(AuthenticationMethod.MOJANG, sessions.getActive(account.minecraftUuid)?.authenticationMethod)
            }
            false -> assertIs<VerifiedMojangAuthenticationResult.PasswordRequired>(result.get(2, TimeUnit.SECONDS))
            null -> assertFails { result.get(2, TimeUnit.SECONDS) }
        }
        if (enabled != true) {
            assertNull(sessions.getActive(account.minecraftUuid))
            assertEquals(ConnectionState.PRE_AUTH, sessions.get(context.connectionId)?.state)
        }
    }

    private fun exercise(mode: String) {
        val now = Instant.parse("2026-10-10T00:00:00Z")
        val name = AccountUsername.parse("PremiumRace")
        val account = AuthAccount(AccountId.random(), name, IdentityType.MOJANG, UUID.randomUUID(), AccountState.REGISTERED, now, now)
        val sessions = InMemorySessionRegistry()
        val connecting = AuthSession.connecting(ConnectionId.random(), name, InetAddress.getLoopbackAddress(), now)
        sessions.create(connecting)
        sessions.enterPreAuth(connecting.connectionId)
        val context = VerifiedMojangAuthenticationContext(
            connecting.connectionId, name, connecting.sourceAddress, account.minecraftUuid, connecting.sessionNonce,
        )
        val binding = CompletableFuture<MojangIdentityBindingResult>()
        val admission = CompletableFuture<Boolean>()
        val storage = Proxy.newProxyInstance(AccountStorage::class.java.classLoader, arrayOf(AccountStorage::class.java)) { _, method, _ ->
            when {
                method.name == "bindVerifiedMojangIdentity" -> binding
                method.name.startsWith("isPremiumLoginEnabled") -> CompletableFuture.completedFuture(true)
                else -> error("Unexpected storage call ${method.name}")
            }
        } as AccountStorage
        var audits = 0
        val service = VerifiedMojangAuthenticationService(storage, sessions, SecurityAuditSink {
            audits++; CompletableFuture.completedFuture(null)
        }, Clock.fixed(now, ZoneOffset.UTC), AccountActivationGuard { _, _ -> admission })
        val result = service.authenticate(context).toCompletableFuture()
        if (mode == "null") {
            binding.complete(null)
            assertFails { result.get(2, TimeUnit.SECONDS) }
            assertNull(sessions.getActive(account.minecraftUuid))
            assertEquals(0, audits)
            return
        }
        binding.complete(MojangIdentityBindingResult.Bound(account, false))
        assertFalse(result.isDone)
        when (mode) {
            "deny" -> admission.complete(false)
            "failure" -> admission.completeExceptionally(IllegalStateException("provider unavailable"))
            "disconnect" -> { sessions.disconnect(context.connectionId); admission.complete(true) }
            "reconnect" -> {
                sessions.disconnect(context.connectionId)
                sessions.create(AuthSession.connecting(context.connectionId, name, context.sourceAddress, now))
                sessions.enterPreAuth(context.connectionId)
                admission.complete(true)
            }
        }
        if (mode == "deny") assertEquals(VerifiedMojangAuthenticationResult.AccessDenied, result.get(2, TimeUnit.SECONDS))
        else assertFails { result.get(2, TimeUnit.SECONDS) }
        assertNull(sessions.getActive(account.minecraftUuid))
        assertEquals(0, audits)
    }
}
