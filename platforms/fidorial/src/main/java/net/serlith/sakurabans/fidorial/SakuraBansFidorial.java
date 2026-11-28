package net.serlith.sakurabans.fidorial;

import fr.fidorial.plugin.Plugin;
import fr.fidorial.plugin.PluginContext;
import net.serlith.sakurabans.core.SakuraBansCore;
import net.serlith.sakurabans.core.bootstrap.CoreBootstrap;
import net.serlith.sakurabans.core.bootstrap.CoreEnvironment;

public class SakuraBansFidorial implements Plugin {

    private PluginContext ctx;
    private SakuraBansCore core;

    @Override
    public void onLoad(final PluginContext ctx) {
        this.ctx = ctx;
        this.core = CoreBootstrap.create(
                new CoreEnvironment(ctx.dataFolder(), ctx.logger(), false, System.getenv()));
    }

}
