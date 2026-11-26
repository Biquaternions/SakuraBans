package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import net.serlith.sakurabans.core.enforce.ChatDecision;
import net.serlith.sakurabans.core.enforce.ChatGate;
import net.serlith.sakurabans.core.enforce.SignedChatPolicy;
import net.serlith.sakurabans.core.platform.OnlinePlayer;

final class VelocityChatListener {

    private final ChatGate gate;
    private final VelocityPlatform platform;
    private final SignedChatPolicy policy;

    VelocityChatListener(final ChatGate gate, final VelocityPlatform platform,
            final SignedChatPolicy policy) {
        this.gate = gate;
        this.platform = platform;
        this.policy = policy;
    }

    // setResult is deprecated because a denial kicks 1.19.1+ clients; the policy only lets it
    // deny when SignedVelocity is there to turn it into a cancellation on the backend.
    @SuppressWarnings("deprecation")
    @Subscribe
    void onChat(final PlayerChatEvent event) {
        if (!event.getResult().isAllowed()) {
            return;
        }
        final Player player = event.getPlayer();
        final OnlinePlayer online = this.platform.online(player);
        final ChatDecision decision = this.gate.checkChat(online, event.getMessage(),
                this.policy.chatCancellable());
        switch (decision) {
            case ChatDecision.Allowed allowed -> {}
            case ChatDecision.Denied denied -> {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(denied.message());
            }
            // The backend never sees it, so the sender is shown their own line.
            case ChatDecision.ShadowMuted shadow -> {
                event.setResult(PlayerChatEvent.ChatResult.denied());
                player.sendMessage(this.gate.shadowEcho(online, event.getMessage()));
            }
        }
    }

    @Subscribe
    void onCommand(final CommandExecuteEvent event) {
        if (!event.getResult().isAllowed()
                || !(event.getCommandSource() instanceof Player player)) {
            return;
        }
        final boolean signedArguments = event.getInvocationInfo().signedState()
                == CommandExecuteEvent.SignedState.SIGNED_WITH_ARGS;
        final ChatDecision decision = this.gate.checkCommand(this.platform.online(player),
                event.getCommand(), this.policy.commandCancellable(signedArguments));
        switch (decision) {
            case ChatDecision.Allowed allowed -> {}
            case ChatDecision.Denied denied -> {
                event.setResult(CommandExecuteEvent.CommandResult.denied());
                player.sendMessage(denied.message());
            }
            case ChatDecision.ShadowMuted shadow ->
                    event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }

}
