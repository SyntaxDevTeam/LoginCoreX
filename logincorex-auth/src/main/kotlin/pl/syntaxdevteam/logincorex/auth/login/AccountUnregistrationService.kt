package pl.syntaxdevteam.logincorex.auth.login

import pl.syntaxdevteam.logincorex.auth.session.AccountActivationGuard
import pl.syntaxdevteam.logincorex.auth.session.InMemorySessionRegistry
import pl.syntaxdevteam.logincorex.domain.account.IdentityType
import pl.syntaxdevteam.logincorex.domain.session.AuthSession
import pl.syntaxdevteam.logincorex.storage.AccountCredentials
import pl.syntaxdevteam.logincorex.storage.AccountStorage
import java.time.Clock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class AccountUnregistrationService(
    private val login: LoginService,
    private val storage: AccountStorage,
    private val sessions: InMemorySessionRegistry,
    private val admission: AccountActivationGuard,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun unregister(session: AuthSession, password: CharArray): CompletionStage<LoginResult> =
        login.verifyForAccountChange(session.username, session.sourceAddress, password) { credentials ->
            val account = credentials.account
            if (account.id != session.accountId || account.identityType != IdentityType.OFFLINE ||
                session.identityType != IdentityType.OFFLINE || account.minecraftUuid != session.minecraftUuid
            ) {
                return@verifyForAccountChange CompletableFuture.completedFuture(false)
            }
            admission.allows(account, session.sourceAddress).thenCompose { allowed ->
                if (allowed && sessions.beginAccountRetirement(session)) commit(session, credentials)
                else CompletableFuture.completedFuture(false)
            }
        }

    private fun commit(session: AuthSession, credentials: AccountCredentials): CompletionStage<Boolean> {
        val update = try {
            storage.unregisterOffline(credentials, session.sourceAddress, clock.instant())
        } catch (failure: Exception) {
            CompletableFuture.failedFuture(failure)
        }
        return update.whenComplete { committed, _ ->
            sessions.finishAccountRetirement(session, committed == true)
        }
    }
}
