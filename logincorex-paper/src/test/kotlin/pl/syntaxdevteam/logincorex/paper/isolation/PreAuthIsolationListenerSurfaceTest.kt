package pl.syntaxdevteam.logincorex.paper.isolation

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.event.EventPriority
import org.bukkit.event.EventHandler
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.*
import org.bukkit.event.vehicle.VehicleEnterEvent
import org.bukkit.event.vehicle.VehicleExitEvent
import org.junit.jupiter.api.Test
import pl.syntaxdevteam.logincorex.auth.session.InMemorySessionRegistry
import pl.syntaxdevteam.logincorex.paper.privacy.ConsentGate
import pl.syntaxdevteam.logincorex.paper.scheduler.PaperPlatformScheduler
import pl.syntaxdevteam.logincorex.security.bot.ConnectionBehaviorGate
import pl.syntaxdevteam.logincorex.security.bot.ConnectionBehaviorPolicy
import pl.syntaxdevteam.logincorex.security.bot.UsernameBurstGate
import pl.syntaxdevteam.logincorex.security.bot.UsernameBurstPolicy
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreAuthIsolationListenerSurfaceTest {
    @Test
    fun `world-affecting event surface remains guarded at highest priority`() {
        cancellableEvents.forEach { eventType ->
            val method = PreAuthIsolationListener::class.java.declaredMethods.singleOrNull {
                it.parameterTypes.contentEquals(arrayOf(eventType))
            }
            assertNotNull(method, "Missing PRE_AUTH guard for ${eventType.simpleName}")
            val handler = method.getAnnotation(EventHandler::class.java)
            assertNotNull(handler, "${method.name} must be a Bukkit event handler")
            assertEquals(EventPriority.HIGHEST, handler.priority)
            assertTrue(handler.ignoreCancelled, "${method.name} must preserve an earlier cancellation")
        }
    }

    @Test
    fun `command suggestion surface is cleared by a dedicated handler`() {
        val method = PreAuthIsolationListener::class.java.getDeclaredMethod(
            "onCommandSuggestions",
            PlayerCommandSendEvent::class.java,
        )
        val handler = method.getAnnotation(EventHandler::class.java)
        assertNotNull(handler)
        assertEquals(EventPriority.HIGHEST, handler.priority)
        assertFalse(handler.ignoreCancelled)
    }

    @Test
    fun `invulnerability is event scoped and never retained in the player snapshot`() {
        val snapshot = PreAuthIsolationManager::class.java.declaredClasses.single {
            it.simpleName == "PlayerSnapshot"
        }

        assertFalse(
            snapshot.declaredFields.any { it.name == "invulnerable" },
            "Persistent Bukkit invulnerability must not be used for connection-scoped PRE_AUTH isolation",
        )
        assertTrue(EntityDamageEvent::class.java in cancellableEvents)
    }

    @Test
    fun `PRE_AUTH handles premium locally without dispatching third-party commands`() {
        val id = UUID.randomUUID()
        val player = Player::class.java.cast(Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Player::class.java),
        ) { _, method, _ ->
            if (method.name == "getUniqueId") id else error("Unexpected Player call: ${method.name}")
        })
        val plugin = Plugin::class.java.cast(Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(Plugin::class.java),
        ) { _, method, _ -> error("Unexpected Plugin call: ${method.name}") })
        var reopenCount = 0
        var preAuth = true
        val access = PreAuthAccess { preAuth }
        val consent = ConsentGate()
        val interval = Duration.ofSeconds(1)
        val listener = PreAuthIsolationListener(
            access, PreAuthIsolationManager(plugin, PaperPlatformScheduler(plugin), access, 20, Component.empty()),
            InMemorySessionRegistry(), PreAuthAdmission(1),
            UsernameBurstGate(UsernameBurstPolicy(2, 2, interval, interval, 10)),
            ConnectionBehaviorGate(ConnectionBehaviorPolicy(5, 1, 1, 1, 1, interval, interval, 10)),
            consent, reopenPremium = { reopenCount++ },
        )
        fun cancelled(command: String): Boolean =
            PlayerCommandPreprocessEvent(player, command, mutableSetOf()).also(listener::onCommand).isCancelled

        assertTrue(cancelled("/PrEmIuM"))
        assertTrue(cancelled("/premium password"))
        assertTrue(cancelled("/logincorex:premium"))
        assertTrue(cancelled("/unregister"))
        assertEquals(1, reopenCount)

        preAuth = false
        assertFalse(cancelled("/premium"))
        consent.prepare(id)
        assertTrue(cancelled("/premium"))
        assertEquals(1, reopenCount)
    }

    private val cancellableEvents = listOf(
        PlayerMoveEvent::class.java,
        PlayerTeleportEvent::class.java,
        PlayerInteractEvent::class.java,
        PlayerInteractEntityEvent::class.java,
        PlayerArmorStandManipulateEvent::class.java,
        PlayerBucketEmptyEvent::class.java,
        PlayerBucketFillEvent::class.java,
        PlayerEditBookEvent::class.java,
        PlayerTakeLecternBookEvent::class.java,
        PlayerShearEntityEvent::class.java,
        PlayerFishEvent::class.java,
        BlockBreakEvent::class.java,
        BlockPlaceEvent::class.java,
        InventoryClickEvent::class.java,
        InventoryDragEvent::class.java,
        InventoryOpenEvent::class.java,
        PlayerDropItemEvent::class.java,
        EntityPickupItemEvent::class.java,
        EntityDamageEvent::class.java,
        EntityDamageByEntityEvent::class.java,
        EntityShootBowEvent::class.java,
        ProjectileLaunchEvent::class.java,
        HangingBreakByEntityEvent::class.java,
        PlayerCommandPreprocessEvent::class.java,
        PlayerPortalEvent::class.java,
        PlayerSwapHandItemsEvent::class.java,
        PlayerItemConsumeEvent::class.java,
        PlayerItemHeldEvent::class.java,
        VehicleEnterEvent::class.java,
        VehicleExitEvent::class.java,
    )
}
