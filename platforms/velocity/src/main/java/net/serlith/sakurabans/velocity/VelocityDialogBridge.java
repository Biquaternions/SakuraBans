package net.serlith.sakurabans.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.protocol.dialog.CommonDialogData;
import com.github.retrooper.packetevents.protocol.dialog.DialogAction;
import com.github.retrooper.packetevents.protocol.dialog.NoticeDialog;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessage;
import com.github.retrooper.packetevents.protocol.dialog.body.PlainMessageDialogBody;
import com.github.retrooper.packetevents.protocol.dialog.button.ActionButton;
import com.github.retrooper.packetevents.protocol.dialog.button.CommonButtonData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerShowDialog;
import com.velocitypowered.api.network.ProtocolState;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import net.kyori.adventure.text.Component;

final class VelocityDialogBridge implements VelocityNotices {

    // The vanilla defaults of a notice dialog's message and button.
    private static final int BODY_WIDTH = 200;
    private static final int BUTTON_WIDTH = 150;

    @Override
    public boolean show(final Player player, final Component title, final Component body,
            final Component button) {
        final PacketEventsAPI<?> api = PacketEvents.getAPI();
        // Dialogs exist since 1.21.6; between servers the client is in the configuration
        // phase, where the play packet can't be sent.
        if (api == null || !api.isInitialized()
                || player.getProtocolVersion().lessThan(ProtocolVersion.MINECRAFT_1_21_6)
                || player.getProtocolState() != ProtocolState.PLAY) {
            return false;
        }
        final NoticeDialog dialog = new NoticeDialog(
                new CommonDialogData(title, null, true, false, DialogAction.CLOSE,
                        List.of(new PlainMessageDialogBody(new PlainMessage(body, BODY_WIDTH))),
                        List.of()),
                new ActionButton(new CommonButtonData(button, null, BUTTON_WIDTH), null));
        api.getPlayerManager().sendPacket(player, new WrapperPlayServerShowDialog(dialog));
        return true;
    }
}
