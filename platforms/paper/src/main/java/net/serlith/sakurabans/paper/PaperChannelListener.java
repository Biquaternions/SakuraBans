package net.serlith.sakurabans.paper;

import net.serlith.sakurabans.core.enforce.ChannelReceiver;
import net.serlith.sakurabans.core.messaging.ProxyChannel;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

final class PaperChannelListener implements PluginMessageListener {

    private final Plugin plugin;
    private final ChannelReceiver receiver;

    PaperChannelListener(final Plugin plugin, final ChannelReceiver receiver) {
        this.plugin = plugin;
        this.receiver = receiver;
    }

    void register() {
        this.plugin.getServer().getMessenger()
                .registerOutgoingPluginChannel(this.plugin, ProxyChannel.NAME);
        this.plugin.getServer().getMessenger()
                .registerIncomingPluginChannel(this.plugin, ProxyChannel.NAME, this);
    }

    @Override
    public void onPluginMessageReceived(final String channel, final Player player,
            final byte[] message) {
        this.receiver.received(message);
    }
}
