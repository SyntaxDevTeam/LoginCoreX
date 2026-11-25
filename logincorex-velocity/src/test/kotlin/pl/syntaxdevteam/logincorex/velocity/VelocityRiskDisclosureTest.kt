package pl.syntaxdevteam.logincorex.velocity

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.Component
import pl.syntaxdevteam.logincorex.domain.account.AccountUsername
import pl.syntaxdevteam.logincorex.integrations.network.IpIntelligence
import pl.syntaxdevteam.logincorex.storage.*
import java.lang.reflect.Proxy
import java.net.InetAddress
import kotlin.test.*

class VelocityRiskDisclosureTest {
    @Test
    fun `alerts require separate grants for IP network risk and linked accounts`() {
        val permissions = mutableSetOf("logincorex.admin.alerts")
        var sent: Component? = null
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "isActive", "isOnlineMode" -> true
                "hasPermission" -> args!![0] in permissions
                "sendMessage" -> { sent = args!![0] as Component; null }
                else -> null
            }
        } as Player
        val channels = Proxy.newProxyInstance(
            com.velocitypowered.api.proxy.messages.ChannelRegistrar::class.java.classLoader,
            arrayOf(com.velocitypowered.api.proxy.messages.ChannelRegistrar::class.java),
        ) { _, _, _ -> null }
        val proxy = Proxy.newProxyInstance(ProxyServer::class.java.classLoader, arrayOf(ProxyServer::class.java)) { _, method, _ ->
            when (method.name) {
                "getAllPlayers" -> listOf(player)
                "getChannelRegistrar" -> channels
                else -> null
            }
        } as ProxyServer
        val risk = ProxyRiskResult(
            MultiAccountReport(listOf(RelatedOfflineAccount(AccountUsername.parse("LinkedAccount"), 1)), false),
            IpIntelligence(vpn = true, proxy = false, tor = false, countryCode = "PL", asn = "AS123", city = "Warsaw"),
            null, false,
        )
        fun deliver(): String {
            sent = null
            VelocityRiskAdmin(proxy, null, VelocityStaffProof(proxy, Any(), "s".repeat(32)), { true },
                { Component.text(it + " <ip> <accounts> <signals> <city>") }, 0, false, true, true)
                .notify("Player", InetAddress.getByName("192.0.2.1"), risk, false)
            return requireNotNull(sent).toString()
        }
        var output = deliver()
        for (secret in listOf("192.0.2.1", "LinkedAccount", "VPN", "Warsaw")) assertFalse(output.contains(secret))
        permissions += "logincorex.admin.view-ip"
        output = deliver()
        assertContains(output, "192.0.2.1")
        assertFalse(output.contains("VPN"))
        permissions -= "logincorex.admin.view-ip"
        permissions += "logincorex.admin.view-geo"
        permissions += "logincorex.admin.alts"
        output = deliver()
        assertFalse(output.contains("192.0.2.1"))
        for (secret in listOf("LinkedAccount", "VPN", "Warsaw")) assertContains(output, secret)
    }
}
