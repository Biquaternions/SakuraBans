package net.serlith.sakurabans.core;

import net.serlith.sakurabans.api.SakuraBansApi;
import net.serlith.sakurabans.api.allow.AllowListService;
import net.serlith.sakurabans.api.data.PlayerDataService;
import net.serlith.sakurabans.api.event.EventBus;
import net.serlith.sakurabans.api.format.MessageFormatter;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.player.PlayerService;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.template.TemplateService;
import net.serlith.sakurabans.core.util.BuildConstants;

public record SakuraApi(PunishmentService punishments, PlayerService players,
        ScopeService scopes, EventBus events, IntegrationRegistry integrations,
        TemplateService templates, AllowListService allowList, MessageFormatter formatter,
        PlayerDataService playerData) implements SakuraBansApi {

    @Override
    public String apiVersion() {
        return BuildConstants.VERSION;
    }
}
