package net.serlith.sakurabans.core.command.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.SakuraCaptions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.util.Scopes;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.SuggestionProvider;

public final class ScopeParser implements ArgumentParser<CommandActor, Scope>,
        BlockingSuggestionProvider.Strings<CommandActor> {

    public static final String ALL = "all";

    private final SakuraScopeService scopes;

    public ScopeParser(final SakuraScopeService scopes) {
        this.scopes = scopes;
    }

    public ParserDescriptor<CommandActor, Scope> descriptor() {
        return ParserDescriptor.of(this, Scope.class);
    }

    /**
     * For configuration overrides: a scope as {@link #descriptor()} reads it, without
     * {@code local} (a configuration scope names its server).
     */
    public ParserDescriptor<CommandActor, Scope> configDescriptor() {
        return ParserDescriptor.of(new ConfigParser(), Scope.class);
    }

    /** For revocations: a scope, or {@code all}. */
    public ParserDescriptor<CommandActor, ScopeFilter> filterDescriptor() {
        return ParserDescriptor.of(new FilterParser(), ScopeFilter.class);
    }

    @Override
    public ArgumentParseResult<Scope> parse(final CommandContext<CommandActor> context,
            final CommandInput input) {
        final String token = input.peekString();
        final Optional<Scope> scope = this.scopes.parse(token);
        if (scope.isEmpty()) {
            return ArgumentParseResult.failure(new ScopeException(context, token));
        }
        input.readString();
        return ArgumentParseResult.success(scope.get());
    }

    @Override
    public Iterable<String> stringSuggestions(final CommandContext<CommandActor> context,
            final CommandInput input) {
        final List<String> suggestions = new ArrayList<>(List.of("global"));
        if (this.scopes.thisServer().isPresent()) {
            suggestions.add("local");
        }
        this.scopes.knownServers().forEach(server -> suggestions.add("server:" + server));
        this.scopes.knownCategories()
                .forEach(category -> suggestions.add("category:" + category));
        return suggestions;
    }

    public SuggestionProvider<CommandActor> servers() {
        return SuggestionProvider.blockingStrings(
                (context, input) -> List.copyOf(this.scopes.knownServers()));
    }

    public SuggestionProvider<CommandActor> categories() {
        return SuggestionProvider.blockingStrings(
                (context, input) -> List.copyOf(this.scopes.knownCategories()));
    }

    private final class ConfigParser implements ArgumentParser<CommandActor, Scope>,
            BlockingSuggestionProvider.Strings<CommandActor> {

        @Override
        public ArgumentParseResult<Scope> parse(final CommandContext<CommandActor> context,
                final CommandInput input) {
            final String token = input.peekString();
            if (token.startsWith("-")) {
                // A flag: the scope was left out, which means global.
                return ArgumentParseResult.success(GlobalScope.INSTANCE);
            }
            final Optional<Scope> scope = Scopes.parse(token);
            if (scope.isEmpty()) {
                return ArgumentParseResult.failure(new ScopeException(context, token));
            }
            input.readString();
            return ArgumentParseResult.success(scope.get());
        }

        @Override
        public Iterable<String> stringSuggestions(final CommandContext<CommandActor> context,
                final CommandInput input) {
            final List<String> suggestions = new ArrayList<>();
            ScopeParser.this.stringSuggestions(context, input).forEach(suggestions::add);
            suggestions.remove("local");
            return suggestions;
        }
    }

    private final class FilterParser implements ArgumentParser<CommandActor, ScopeFilter>,
            BlockingSuggestionProvider.Strings<CommandActor> {

        @Override
        public ArgumentParseResult<ScopeFilter> parse(
                final CommandContext<CommandActor> context, final CommandInput input) {
            if (input.peekString().toLowerCase(Locale.ROOT).equals(ALL)) {
                input.readString();
                return ArgumentParseResult.success(ScopeFilter.ALL);
            }
            return ScopeParser.this.parse(context, input).mapSuccess(ScopeFilter::of);
        }

        @Override
        public Iterable<String> stringSuggestions(final CommandContext<CommandActor> context,
                final CommandInput input) {
            final List<String> suggestions = new ArrayList<>(List.of(ALL));
            ScopeParser.this.stringSuggestions(context, input).forEach(suggestions::add);
            return suggestions;
        }
    }

    public static final class ScopeException extends ParserException {

        ScopeException(final CommandContext<?> context, final String input) {
            super(ScopeParser.class, context, SakuraCaptions.SCOPE,
                    CaptionVariable.of("input", input));
        }
    }
}
