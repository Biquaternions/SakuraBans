package net.serlith.sakurabans.core.command.module;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.CompositeVictim;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.geoip.Country;
import net.serlith.sakurabans.core.geoip.CountryLookup;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class GeoIpCommands implements CommandModule {

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final CountryLookup countries;

    public GeoIpCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final TargetParser targetParser,
            final TargetResolver targets, final CountryLookup countries) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.targetParser = targetParser;
        this.targets = targets;
        this.countries = countries;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        this.registrar.builder(manager, "geoip", MessageKey.COMMAND_DESCRIPTION_GEOIP)
                .ifPresent(builder -> manager.command(builder.permission(Permissions.GEOIP)
                        .required("target", this.targetParser.descriptor())
                        .futureHandler(context -> this.runner.run(context.sender(),
                                () -> this.geoip(context)))));
    }

    private CompletableFuture<?> geoip(final CommandContext<CommandActor> context) {
        final CommandActor actor = context.sender();
        // Asked first, so a server without GeoIP never resolves the target.
        final CountryLookup.State state = this.countries.state();
        if (state != CountryLookup.State.READY) {
            this.runner.send(actor, state == CountryLookup.State.DISABLED
                    ? MessageKey.GEOIP_DISABLED : MessageKey.GEOIP_NO_DATABASE);
            return CompletableFuture.completedFuture(null);
        }
        return this.targets.resolve(context.get("target")).thenAccept(target -> {
            switch (target) {
                case ResolvedTarget.Player player -> {
                    final InetAddress address = player.lastAddress();
                    if (address == null) {
                        this.runner.send(actor, MessageKey.COMMAND_NO_ADDRESS,
                                Placeholder.unparsed("name", PunishCommands.displayName(player)));
                        return;
                    }
                    this.reply(actor, address, this.replies.victim(actor,
                            CompositeVictim.of(player.uuid(), address)));
                }
                case ResolvedTarget.Address found -> this.reply(actor, found.address(),
                        this.replies.victim(actor, AddressVictim.of(found.address())));
                case ResolvedTarget.PunishmentId id ->
                        this.runner.send(actor, MessageKey.COMMAND_ID_NOT_ALLOWED);
                case ResolvedTarget.Range range -> this.runner.send(actor, MessageKey.GEOIP_TARGET);
                case ResolvedTarget.Unknown unknown ->
                        this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                                Placeholder.unparsed("input", HistoryCommands.targetName(target)));
            }
        });
    }

    private void reply(final CommandActor actor, final InetAddress address, final String shown) {
        final Optional<Country> country = this.countries.country(address);
        if (country.isEmpty()) {
            this.runner.send(actor, MessageKey.GEOIP_UNKNOWN,
                    Placeholder.unparsed("target", shown));
            return;
        }
        this.runner.send(actor, MessageKey.GEOIP_RESULT, Placeholder.unparsed("target", shown),
                Placeholders.country(country.get().name()),
                Placeholder.unparsed("code", country.get().code()));
    }
}
