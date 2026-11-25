package pl.syntaxdevteam.logincorex.paper.command

import kotlin.test.Test
import kotlin.test.assertContains

class SensitivePermissionDefaultsTest {
    @Test
    fun `operators do not implicitly receive sensitive permissions`() {
        val descriptor = requireNotNull(javaClass.classLoader.getResourceAsStream("paper-plugin.yml"))
            .bufferedReader().use { it.readText() }
        for (name in listOf("view-ip", "view-geo", "view-security", "alts")) {
            val section = descriptor.substringAfter("  logincorex.admin.$name:")
                .substringBefore("\n  logincorex.")
            assertContains(section, "default: false", message = name)
        }
    }
}
