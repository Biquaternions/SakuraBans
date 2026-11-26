package net.serlith.sakurabans.api;

import net.serlith.sakurabans.api.allow.AllowListService;
import net.serlith.sakurabans.api.data.PlayerDataService;
import net.serlith.sakurabans.api.event.EventBus;
import net.serlith.sakurabans.api.format.MessageFormatter;
import net.serlith.sakurabans.api.integration.IntegrationRegistry;
import net.serlith.sakurabans.api.player.PlayerService;
import net.serlith.sakurabans.api.punish.PunishmentService;
import net.serlith.sakurabans.api.scope.ScopeService;
import net.serlith.sakurabans.api.template.TemplateService;

/**
 * Entry point of the SakuraBans API. Get it with {@link SakuraBans#api()}, or on Paper from the
 * {@code ServicesManager}. Implemented by SakuraBans; don't implement it.
 */
public interface SakuraBansApi {

    /**
     * The version of this API, following semantic versioning: a new minor version only adds,
     * and a new major version may remove or change.
     *
     * @return the version, for example {@code 1.0.0}
     */
    String apiVersion();

    /**
     * Punishments: create, revoke, edit, find.
     *
     * @return the service
     */
    PunishmentService punishments();

    /**
     * Accounts, names, addresses and alts.
     *
     * @return the service
     */
    PlayerService players();

    /**
     * Scope parsing and resolution.
     *
     * @return the service
     */
    ScopeService scopes();

    /**
     * Templates and ladder progress.
     *
     * @return the service
     */
    TemplateService templates();

    /**
     * The allow list.
     *
     * @return the service
     */
    AllowListService allowList();

    /**
     * The event bus.
     *
     * @return the bus
     */
    EventBus events();

    /**
     * Rendering punishments with the configured messages.
     *
     * @return the formatter
     */
    MessageFormatter formatter();

    /**
     * Where other plugins register profile sources.
     *
     * @return the registry
     */
    IntegrationRegistry integrations();

    /**
     * Exporting and erasing player data.
     *
     * @return the service
     */
    PlayerDataService playerData();
}
