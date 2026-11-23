package net.serlith.sakurabans.core;

import java.util.Set;
import net.serlith.sakurabans.api.Capability;
import net.serlith.sakurabans.api.SakuraBansApi;
import net.serlith.sakurabans.api.allow.AllowListService;
import net.serlith.sakurabans.api.appeal.AppealService;
import net.serlith.sakurabans.api.audit.AuditService;
import net.serlith.sakurabans.api.auth.AuthorizationService;
import net.serlith.sakurabans.api.data.PlayerDataService;
import net.serlith.sakurabans.api.event.EventBus;
import net.serlith.sakurabans.api.format.MessageFormatter;
import net.serlith.sakurabans.api.imports.ImportService;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.kind.KindService;
import net.serlith.sakurabans.api.message.MessageTemplateService;
import net.serlith.sakurabans.api.player.PlayerService;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.settings.SettingsService;
import net.serlith.sakurabans.api.stats.StatsService;
import net.serlith.sakurabans.api.template.TemplateService;
import net.serlith.sakurabans.api.wave.BanWaveService;
import net.serlith.sakurabans.core.util.BuildConstants;

public record SakuraApi(PunishmentService punishments, PlayerService players,
        ScopeService scopes, EventBus events, IntegrationRegistry integrations,
        TemplateService templates, AllowListService allowList, MessageFormatter formatter,
        AuthorizationService authorization, KindService kinds, SettingsService settings,
        MessageTemplateService messages, StatsService stats, AuditService audit,
        BanWaveService banWaves, AppealService appeals, PlayerDataService playerData,
        ImportService imports) implements SakuraBansApi {

    public static SakuraApi create(final PunishmentService punishments,
            final PlayerService players, final ScopeService scopes, final EventBus events,
            final IntegrationRegistry integrations, final AllowListService allowList) {
        return new SakuraApi(punishments, players, scopes, events, integrations,
                Unsupported.service(TemplateService.class, "S31"),
                allowList,
                Unsupported.service(MessageFormatter.class, "S37a"),
                Unsupported.service(AuthorizationService.class, "S33"),
                Unsupported.service(KindService.class, "S31a"),
                Unsupported.service(SettingsService.class, "S37a"),
                Unsupported.service(MessageTemplateService.class, "S37a"),
                Unsupported.service(StatsService.class, "S37b"),
                Unsupported.service(AuditService.class, "S35"),
                Unsupported.service(BanWaveService.class, "S37c"),
                Unsupported.service(AppealService.class, "S37d"),
                Unsupported.service(PlayerDataService.class, "S37e"),
                Unsupported.service(ImportService.class, "S38"));
    }

    @Override
    public String apiVersion() {
        return BuildConstants.VERSION;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.SHADOW_MUTE, Capability.CURSORS);
    }
}
