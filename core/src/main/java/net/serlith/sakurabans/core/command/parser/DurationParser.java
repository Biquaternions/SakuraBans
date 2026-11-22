package net.serlith.sakurabans.core.command.parser;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.SakuraCaptions;
import net.serlith.sakurabans.core.util.Durations;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public final class DurationParser implements ArgumentParser<CommandActor, Duration>,
        BlockingSuggestionProvider.Strings<CommandActor> {

    static final List<String> SAMPLES = List.of("30m", "1h", "12h", "1d", "3d", "7d", "14d", "30d");

    public static ParserDescriptor<CommandActor, Duration> descriptor() {
        return ParserDescriptor.of(new DurationParser(), Duration.class);
    }

    @Override
    public ArgumentParseResult<Duration> parse(final CommandContext<CommandActor> context,
            final CommandInput input) {
        final String token = input.peekString();
        final Optional<Duration> duration = Durations.parse(token);
        if (duration.isEmpty()) {
            return ArgumentParseResult.failure(new DurationException(context, token));
        }
        input.readString();
        return ArgumentParseResult.success(duration.get());
    }

    @Override
    public Iterable<String> stringSuggestions(final CommandContext<CommandActor> context,
            final CommandInput input) {
        return SAMPLES;
    }

    public static final class DurationException extends ParserException {

        DurationException(final CommandContext<?> context, final String input) {
            super(DurationParser.class, context, SakuraCaptions.DURATION,
                    CaptionVariable.of("input", input));
        }
    }
}
