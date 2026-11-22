package net.serlith.sakurabans.core.command.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.Scope;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.core.command.CommandActor;
import net.serlith.sakurabans.core.command.SakuraCaptions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

public final class ScopeParser implements ArgumentParser<CommandActor, Scope>,
        BlockingSuggestionProvider.Strings<CommandActor> {

    private final SakuraScopeService scopes;

    public ScopeParser(final SakuraScopeService scopes) {
        this.scopes = scopes;
    }

    public ParserDescriptor<CommandActor, Scope> descriptor() {
        return ParserDescriptor.of(this, Scope.class);
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
        final Optional<ServerScope> own = this.scopes.thisServer();
        if (own.isPresent()) {
            suggestions.add("local");
            this.scopes.applicableScopes(own.get()).forEach(scope -> {
                if (!(scope instanceof GlobalScope)) {
                    suggestions.add(this.scopes.format(scope));
                }
            });
        }
        return suggestions;
    }

    public static final class ScopeException extends ParserException {

        ScopeException(final CommandContext<?> context, final String input) {
            super(ScopeParser.class, context, SakuraCaptions.SCOPE,
                    CaptionVariable.of("input", input));
        }
    }
}
