package net.serlith.sakurabans.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.serlith.sakurabans.core.enforce.ChannelReceiver;
import net.serlith.sakurabans.core.messaging.ProxyChannel;

final class VelocityChannelListener {

    static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(ProxyChannel.NAME);

    private final ProxyChannel channel;
    private final ChannelReceiver receiver;

    VelocityChannelListener(final ProxyChannel channel, final ChannelReceiver receiver) {
        this.channel = channel;
        this.receiver = receiver;
    }

    void register(final ProxyServer proxy, final Object plugin) {
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getEventManager().register(plugin, this);
    }

    @Subscribe
    void onPluginMessage(final PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (event.getSource() instanceof ServerConnection) {
            this.receiver.received(event.getData());
        }
    }

    @Subscribe
    void onServerPostConnect(final ServerPostConnectEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(CHANNEL,
                        this.channel.serverName(connection.getServerInfo().getName())));
    }
}
