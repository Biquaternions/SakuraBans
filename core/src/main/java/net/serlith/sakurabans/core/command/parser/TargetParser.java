package net.serlith.sakurabans.core.command.parser;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.SakuraCaptions;
import net.serlith.sakurabans.core.concurrent.Lifecycle;
import net.serlith.sakurabans.core.model.TargetInput;
import net.serlith.sakurabans.core.platform.OnlinePlayer;
import net.serlith.sakurabans.core.platform.Platform;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.util.Cidrs;
import net.serlith.sakurabans.core.util.InetAddresses;
import net.serlith.sakurabans.core.util.Strings;
import net.serlith.sakurabans.core.util.Uuids;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class TargetParser implements ArgumentParser<CommandActor, TargetInput> {

    private static final int SUGGESTIONS = 30;

    private final Platform platform;
    private final NameCache names;
    private final Lifecycle lifecycle;

    public TargetParser(final Platform platform, final NameCache names,
            final Lifecycle lifecycle) {
        this.platform = platform;
        this.names = names;
        this.lifecycle = lifecycle;
    }

    public ParserDescriptor<CommandActor, TargetInput> descriptor() {
        return ParserDescriptor.of(this, TargetInput.class);
    }

    @Override
    public ArgumentParseResult<TargetInput> parse(final CommandContext<CommandActor> context,
            final CommandInput input) {
        final String token = input.peekString();
        final Optional<TargetInput> parsed = parse(token);
        if (parsed.isEmpty()) {
            return ArgumentParseResult.failure(new TargetException(context, token));
        }
        input.readString();
        return ArgumentParseResult.success(parsed.get());
    }

    public static Optional<TargetInput> parse(final String token) {
        if (token.startsWith("#")) {
            try {
                final long id = Long.parseLong(token.substring(1));
                return id >= 1 ? Optional.of(new TargetInput.IdInput(id)) : Optional.empty();
            } catch (final NumberFormatException e) {
                return Optional.empty();
            }
        }
        final Optional<TargetInput> uuid = Uuids.parse(token).map(TargetInput.UuidInput::new);
        if (uuid.isPresent()) {
            return uuid;
        }
        if (token.contains("/") || token.contains("*")) {
            return Cidrs.parse(token).map(TargetInput.RangeInput::new);
        }
        final Optional<TargetInput> address =
                InetAddresses.parseLiteral(token).map(TargetInput.AddressInput::new);
        if (address.isPresent()) {
            return address;
        }
        return Strings.isValidPlayerName(token)
                ? Optional.of(new TargetInput.NameInput(token))
                : Optional.empty();
    }

    @Override
    public SuggestionProvider<CommandActor> suggestionProvider() {
        return (BlockingSuggestionProvider.Strings<CommandActor>) this::suggest;
    }

    private Iterable<String> suggest(final CommandContext<CommandActor> context,
            final CommandInput input) {
        if (!this.lifecycle.running()) {
            return Set.of();
        }
        final String prefix = input.peekString().toLowerCase(Locale.ROOT);
        final Set<String> suggestions = new LinkedHashSet<>();
        for (final OnlinePlayer player : this.platform.onlinePlayers()) {
            if (player.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                suggestions.add(player.name());
            }
        }
        suggestions.addAll(this.names.suggestions(prefix, SUGGESTIONS));
        return suggestions.stream().limit(SUGGESTIONS).toList();
    }

    public static final class TargetException extends ParserException {

        TargetException(final CommandContext<?> context, final String input) {
            super(TargetParser.class, context, SakuraCaptions.TARGET,
                    CaptionVariable.of("input", input));
        }
    }

}
