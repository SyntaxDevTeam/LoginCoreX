package pl.syntaxdevteam.logincorex.paper.isolation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationFormCommandTest {
    @Test
    fun `pre auth permits only argument free form commands`() {
        listOf("/login", "/register", "/LOGIN", " /register ").forEach {
            assertTrue(isAuthenticationFormCommand(it), it)
        }
        listOf("/login secret", "/register secret secret", "/premium", "/unregister",
            "/minecraft:login", "/other:register", "/login\nsecret", "/login;op", "/privacy", "/lcx reload").forEach {
            assertFalse(isAuthenticationFormCommand(it), it)
        }
    }
}
