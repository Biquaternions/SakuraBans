package net.serlith.sakurabans.core;

import java.util.Set;
import net.serlith.sakurabans.api.Capability;
import net.serlith.sakurabans.api.SakuraBansApi;
import net.serlith.sakurabans.api.allow.AllowListService;
import net.serlith.sakurabans.api.audit.AuditService;
import net.serlith.sakurabans.api.auth.AuthorizationService;
import net.serlith.sakurabans.api.data.PlayerDataService;
import net.serlith.sakurabans.api.event.EventBus;
import net.serlith.sakurabans.api.format.MessageFormatter;
import net.serlith.sakurabans.api.imports.ImportService;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.kind.KindService;
import net.serlith.sakurabans.api.player.PlayerService;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.stats.StatsService;
import net.serlith.sakurabans.api.template.TemplateService;
import net.serlith.sakurabans.core.util.BuildConstants;

public record SakuraApi(PunishmentService punishments, PlayerService players,
        ScopeService scopes, EventBus events, IntegrationRegistry integrations,
        TemplateService templates, AllowListService allowList, MessageFormatter formatter,
        AuthorizationService authorization, KindService kinds, StatsService stats,
        AuditService audit, PlayerDataService playerData, ImportService imports)
        implements SakuraBansApi {

    public static SakuraApi create(final PunishmentService punishments,
            final PlayerService players, final ScopeService scopes, final EventBus events,
            final IntegrationRegistry integrations, final AllowListService allowList,
            final TemplateService templates, final KindService kinds,
            final AuthorizationService authorization, final AuditService audit,
            final MessageFormatter formatter, final StatsService stats,
            final PlayerDataService playerData, final ImportService imports) {
        return new SakuraApi(punishments, players, scopes, events, integrations, templates,
                allowList, formatter, authorization, kinds, stats, audit, playerData, imports);
    }

    @Override
    public String apiVersion() {
        return BuildConstants.VERSION;
    }

    @Override
    public Set<Capability> capabilities() {
        return Set.of(Capability.SHADOW_MUTE, Capability.CURSORS, Capability.KINDS,
                Capability.EVIDENCE, Capability.VOID, Capability.AUDIT, Capability.ROLLBACK,
                Capability.STATS, Capability.PLAYER_DATA, Capability.IMPORT);
    }
}
