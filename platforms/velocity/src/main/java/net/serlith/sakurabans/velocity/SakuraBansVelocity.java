package net.serlith.sakurabans.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import net.serlith.sakurabans.core.SakuraBansCore;
import org.slf4j.Logger;

@Plugin(
    id = "sakurabans",
    name = "SakuraBans",
    version = "1.0-SNAPSHOT"
    ,authors = {"Biquaternions"}
)
public class SakuraBansVelocity implements SakuraBansCore {

    @Inject private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
      // Plugin initialization logic goes here
    }

}
