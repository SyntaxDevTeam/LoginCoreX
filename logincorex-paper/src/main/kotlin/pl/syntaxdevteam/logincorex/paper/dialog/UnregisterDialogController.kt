@file:Suppress("UnstableApiUsage")

package pl.syntaxdevteam.logincorex.paper.dialog

import io.papermc.paper.connection.PlayerGameConnection
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.event.player.PlayerCustomClickEvent
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import pl.syntaxdevteam.logincorex.auth.login.AccountUnregistrationService
import pl.syntaxdevteam.logincorex.auth.login.LoginResult
import pl.syntaxdevteam.logincorex.auth.session.SessionRegistry
import pl.syntaxdevteam.logincorex.domain.account.IdentityType
import pl.syntaxdevteam.logincorex.domain.session.AuthSession
import pl.syntaxdevteam.logincorex.paper.scheduler.PaperPlatformScheduler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class UnregisterDialogText(
    val title: Component,
    val prompt: Component,
    val password: Component,
    val confirm: Component,
    val cancel: Component,
    val unavailable: Component,
    val denied: Component,
    val locked: Component,
    val rateLimited: Component,
    val success: Component,
    val failure: Component,
)

class UnregisterDialogController(
    private val service: AccountUnregistrationService,
    private val sessions: SessionRegistry,
    private val scheduler: PaperPlatformScheduler,
    private val canOpen: (Player) -> Boolean,
    private val text: UnregisterDialogText,
    private val onFailure: (Throwable) -> Unit,
) : Listener {
    private class Form(val player: Player, val session: AuthSession) {
        val submit = Key.key("logincorex", "unregister/${UUID.randomUUID()}")
        val expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
    }

    private val forms = ConcurrentHashMap<UUID, Form>()
    private val submissions = ConcurrentHashMap<UUID, Form>()

    fun isSubmitting(player: Player): Boolean = submissions[player.uniqueId]?.player === player

    fun open(player: Player) {
        if (!canOpen(player) || isSubmitting(player)) return
        val session = sessions.getActive(player.uniqueId)
        if (session == null || session.identityType != IdentityType.OFFLINE) {
            player.sendMessage(text.unavailable)
            return
        }
        val form = Form(player, session)
        forms[player.uniqueId] = form
        player.showDialog(Dialog.create { builder ->
            builder.empty().base(DialogBase.builder(text.title)
                .canCloseWithEscape(true).pause(false)
                .body(listOf(DialogBody.plainMessage(text.prompt, 380)))
                .inputs(listOf(DialogInput.text(PASSWORD, text.password).width(300).maxLength(128).build()))
                .build())
                .type(DialogType.confirmation(
                    ActionButton.builder(text.confirm).action(DialogAction.customClick(form.submit, null)).build(),
                    ActionButton.builder(text.cancel).build(),
                ))
        })
    }

    @EventHandler
    fun onClick(event: PlayerCustomClickEvent) {
        val player = (event.commonConnection as? PlayerGameConnection)?.player ?: return
        val form = forms[player.uniqueId] ?: return
        if (form.player !== player || form.submit != event.identifier || !forms.remove(player.uniqueId, form)) return
        if (System.nanoTime() - form.expires >= 0 || !canOpen(player) ||
            sessions.getActive(player.uniqueId) !== form.session
        ) {
            player.sendMessage(text.denied)
            return
        }
        val input = event.dialogResponseView?.getText(PASSWORD) ?: return
        if (input.length !in 1..128 || submissions.putIfAbsent(player.uniqueId, form) != null) return
        val password = input.toCharArray()
        val operation = try {
            service.unregister(form.session, password)
        } catch (failure: Exception) {
            java.util.concurrent.CompletableFuture.failedFuture(failure)
        }
        operation.whenComplete { result, failure ->
            password.fill('\u0000')
            failure?.let(onFailure)
            scheduler.entity(player) {
                if (submissions[player.uniqueId] !== form) return@entity
                if (player.isOnline) {
                    when {
                        failure != null -> player.sendMessage(text.failure)
                        result is LoginResult.Success -> player.kick(text.success)
                        result == LoginResult.AccountLocked -> player.sendMessage(text.locked)
                        result == LoginResult.RateLimited -> player.sendMessage(text.rateLimited)
                        else -> player.sendMessage(text.denied)
                    }
                }
                submissions.remove(player.uniqueId, form)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        forms.computeIfPresent(player.uniqueId) { _, form -> form.takeUnless { it.player === player } }
        submissions.computeIfPresent(player.uniqueId) { _, form -> form.takeUnless { it.player === player } }
    }

    companion object {
        private const val PASSWORD = "unregister_password"
    }
}
