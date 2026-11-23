package net.serlith.sakurabans.core.command.module;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.serlith.sakurabans.api.model.AddressRangeVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.punish.PunishmentQuery;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.CommandModule;
import net.serlith.sakurabans.core.command.CommandRegistrar;
import net.serlith.sakurabans.core.command.CommandRunner;
import net.serlith.sakurabans.core.command.Pager;
import net.serlith.sakurabans.core.command.Replies;
import net.serlith.sakurabans.core.command.parser.TargetParser;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.message.MessageKey;
import net.serlith.sakurabans.core.model.ResolvedTarget;
import net.serlith.sakurabans.core.permission.Permissions;
import net.serlith.sakurabans.core.player.TargetResolver;
import net.serlith.sakurabans.core.punish.Applicabilities;
import net.serlith.sakurabans.core.punish.SakuraPunishmentService;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

public final class CheckCommands implements CommandModule {

    private record Spec(String name, PunishmentType type, MessageKey description) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec("checkban", PunishmentType.BAN, MessageKey.COMMAND_DESCRIPTION_CHECKBAN),
            new Spec("checkmute", PunishmentType.MUTE, MessageKey.COMMAND_DESCRIPTION_CHECKMUTE),
            new Spec("checkwarn", PunishmentType.WARN, MessageKey.COMMAND_DESCRIPTION_CHECKWARN));

    private final CommandRegistrar registrar;
    private final CommandRunner runner;
    private final Replies replies;
    private final Pager pager;
    private final TargetParser targetParser;
    private final TargetResolver targets;
    private final SakuraPunishmentService punishments;
    private final ConfigService config;

    public CheckCommands(final CommandRegistrar registrar, final CommandRunner runner,
            final Replies replies, final Pager pager, final TargetParser targetParser,
            final TargetResolver targets, final SakuraPunishmentService punishments,
            final ConfigService config) {
        this.registrar = registrar;
        this.runner = runner;
        this.replies = replies;
        this.pager = pager;
        this.targetParser = targetParser;
        this.targets = targets;
        this.punishments = punishments;
        this.config = config;
    }

    @Override
    public void register(final CommandManager<CommandActor> manager) {
        for (final Spec spec : SPECS) {
            this.registrar.builder(manager, spec.name(), spec.description())
                    .ifPresent(builder -> manager.command(builder
                            .permission(Permissions.check(spec.type()))
                            .required("target", this.targetParser.descriptor())
                            .futureHandler(context -> this.runner.run(context.sender(),
                                    () -> this.check(context, spec)))));
        }
    }

    private CompletableFuture<?> check(final CommandContext<CommandActor> context,
            final Spec spec) {
        final CommandActor actor = context.sender();
        return this.targets.resolve(context.get("target")).thenCompose(target -> {
            final PunishmentQuery.Applicability applicability = switch (target) {
                case ResolvedTarget.Player player -> Applicabilities.of(player.uuid(),
                        player.lastAddress(),
                        this.config.settings().enforcement().addressStrictness());
                case ResolvedTarget.Address address -> PunishmentQuery.Applicability.of(null,
                        address.address(), this.config.settings().enforcement()
                                .addressStrictness());
                default -> null;
            };
            if (target instanceof ResolvedTarget.PunishmentId(long id1)) {
                return this.punishments.byId(id1).thenAccept(found -> found.ifPresentOrElse(
                        punishment -> this.runner.send(actor, MessageKey.CHECK_DETAIL,
                                this.replies.punishment(actor, punishment)),
                        () -> this.runner.send(actor, MessageKey.REVOKE_FAILURE_NOT_FOUND)));
            }
            final PunishmentQuery.Builder query = PunishmentQuery.builder().types(spec.type())
                    .activeOnly(true).pageSize(HistoryCommands.PAGE_SIZE);
            if (target instanceof ResolvedTarget.Range(AddressRangeVictim range1)) {
                query.victim(range1);
            } else if (applicability != null) {
                query.applicableTo(applicability);
            } else {
                this.runner.send(actor, MessageKey.COMMAND_UNKNOWN_TARGET,
                        Placeholder.unparsed("input", HistoryCommands.targetName(target)));
                return CompletableFuture.completedFuture(null);
            }
            final String name = HistoryCommands.targetName(target);
            return this.punishments.query(query.build())
                    .thenAccept(page -> {
                        if (page.total() == 0) {
                            this.runner.send(actor, MessageKey.CHECK_NONE,
                                    Placeholder.unparsed("name", name));
                        } else {
                            this.pager.punishments(actor, MessageKey.CHECK_HEADER,
                                    Placeholder.unparsed("name", name), page,
                                    this.registrar.label(spec.name()).orElse(spec.name())
                                            + " " + name);
                        }
                    });
        });
    }
}
