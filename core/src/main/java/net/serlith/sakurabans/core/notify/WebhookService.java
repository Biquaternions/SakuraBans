package net.serlith.sakurabans.core.notify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.serlith.sakurabans.api.event.EventPriority;
import net.serlith.sakurabans.api.event.PunishmentCreatedEvent;
import net.serlith.sakurabans.api.event.PunishmentEditedEvent;
import net.serlith.sakurabans.api.event.PunishmentExpungedEvent;
import net.serlith.sakurabans.api.event.PunishmentRevokedEvent;
import net.serlith.sakurabans.api.event.PunishmentVoidedEvent;
import net.serlith.sakurabans.api.event.PunishmentsRolledBackEvent;
import net.serlith.sakurabans.api.event.Subscription;
import net.serlith.sakurabans.api.model.Punishment;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.PunishmentEdit;
import net.serlith.sakurabans.core.concurrent.SakuraExecutors;
import net.serlith.sakurabans.core.configuration.ConfigService;
import net.serlith.sakurabans.core.configuration.settings.WebhookSettings;
import net.serlith.sakurabans.core.event.SakuraEventBus;
import net.serlith.sakurabans.core.event.SakuraPunishmentRevokedEvent;
import net.serlith.sakurabans.core.message.Formats;
import net.serlith.sakurabans.core.message.MessageService;
import net.serlith.sakurabans.core.message.Placeholders;
import net.serlith.sakurabans.core.player.NameCache;
import net.serlith.sakurabans.core.punish.ConsoleActions;
import net.serlith.sakurabans.core.scope.SakuraScopeService;
import net.serlith.sakurabans.core.util.Futures;
import org.jspecify.annotations.Nullable;

public final class WebhookService {

    static final int ATTEMPTS = 4;
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(1);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern RETRY_AFTER =
            Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");

    private final SakuraEventBus events;
    private final ConfigService config;
    private final MessageService messages;
    private final Placeholders placeholders;
    private final Formats formats;
    private final SakuraScopeService scopes;
    private final NameCache names;
    private final SakuraExecutors executors;
    private final InstantSource clock;
    private final ComponentLogger logger;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Map<URI, CompletableFuture<Void>> queues = new ConcurrentHashMap<>();
    private final AtomicReference<@Nullable HttpClient> client = new AtomicReference<>();

    public WebhookService(final SakuraEventBus events, final ConfigService config,
            final MessageService messages, final Placeholders placeholders,
            final Formats formats, final SakuraScopeService scopes, final NameCache names,
            final SakuraExecutors executors, final InstantSource clock,
            final ComponentLogger logger) {
        this.events = events;
        this.config = config;
        this.messages = messages;
        this.placeholders = placeholders;
        this.formats = formats;
        this.scopes = scopes;
        this.names = names;
        this.executors = executors;
        this.clock = clock;
        this.logger = logger;
    }

    public void start() {
        this.client.set(HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .executor(this.executors.worker())
                .build());
        this.subscriptions.add(this.events.subscribe(PunishmentCreatedEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.punishment(event.punishment().type().name(), event.punishment(),
                                event.punishment().visibility(), Map.of());
                    }
                }));
        this.subscriptions.add(this.events.subscribe(PunishmentRevokedEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.punishment("UN" + event.punishment().type().name(),
                                event.punishment(), event instanceof SakuraPunishmentRevokedEvent
                                        revoked ? revoked.visibility()
                                        : event.punishment().visibility(), Map.of());
                    }
                }));
        this.subscriptions.add(this.events.subscribe(PunishmentVoidedEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.punishment("void", event.punishment(), Visibility.NORMAL,
                                Map.of());
                    }
                }));
        this.subscriptions.add(this.events.subscribe(PunishmentExpungedEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.punishment("delete", event.punishment(), Visibility.NORMAL,
                                Map.of());
                    }
                }));
        this.subscriptions.add(this.events.subscribe(PunishmentEditedEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.punishment("edit", event.punishment(),
                                event.punishment().visibility(),
                                Map.of("edit", this.describe(event.edit())));
                    }
                }));
        this.subscriptions.add(this.events.subscribe(PunishmentsRolledBackEvent.class,
                EventPriority.MONITOR, event -> {
                    if (!event.remote()) {
                        this.rolledBack(event);
                    }
                }));
    }

    public void stop() {
        this.subscriptions.forEach(Subscription::unsubscribe);
        this.subscriptions.clear();
        final HttpClient current = this.client.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }

    private void punishment(final String event, final Punishment punishment,
            final Visibility visibility, final Map<String, String> extra) {
        final WebhookSettings settings = this.config.webhooks();
        if (visibility == Visibility.HIDDEN || punishment.visibility() == Visibility.HIDDEN
                || visibility == Visibility.SILENT && !settings.includeSilent()) {
            return;
        }
        settings.route(event.toLowerCase(Locale.ROOT)).ifPresent(route -> {
            final Map<String, String> values = this.values(punishment);
            values.putAll(extra);
            this.send(settings, route, values);
        });
    }

    private void rolledBack(final PunishmentsRolledBackEvent event) {
        final WebhookSettings settings = this.config.webhooks();
        settings.route("rollback").ifPresent(route -> {
            final Locale locale = this.messages.defaultLocale();
            final Map<String, String> values = new HashMap<>();
            values.put("victim", this.placeholders.operator(event.request().target(), locale));
            values.put("revoker", this.placeholders.operator(event.request().actor().operator(),
                    locale));
            values.put("revoke_reason", event.request().reason().orElse(""));
            values.put("count", Integer.toString(event.punishments().size()));
            this.send(settings, route, values);
        });
    }

    private Map<String, String> values(final Punishment punishment) {
        final Locale locale = this.messages.defaultLocale();
        final Map<String, String> values =
                new HashMap<>(ConsoleActions.values(punishment, this.names::name));
        values.put("display_id", punishment.displayId());
        values.put("duration", this.formats.length(punishment, locale));
        values.put("expires", this.formats.expiry(punishment, locale));
        values.put("type", this.messages.renderPlain(Placeholders.typeKey(punishment.type()),
                locale));
        values.put("kind", punishment.kind());
        values.put("scope", this.scopes.format(punishment.scope()));
        values.put("server", punishment.originServer().orElse(""));
        values.put("revoker", punishment.end().flatMap(end -> end.actor())
                .map(actor -> this.placeholders.operator(actor.operator(), locale))
                .orElse(""));
        values.put("revoke_reason", punishment.end().flatMap(end -> end.reason())
                .or(() -> punishment.voiding().flatMap(voiding -> voiding.reason()))
                .orElse(""));
        if (punishment.type() == PunishmentType.KICK) {
            values.put("duration", "");
        }
        return values;
    }

    private String describe(final PunishmentEdit edit) {
        final Locale locale = this.messages.defaultLocale();
        return switch (edit) {
            case PunishmentEdit.NewReason reason -> "reason: " + reason.reason();
            case PunishmentEdit.NewExpiry expiry -> "expires: " + expiry.expiresAt()
                    .map(at -> this.formats.instant(at, locale)).orElse("never");
            case PunishmentEdit.NewScope scope -> "scope: " + this.scopes.format(scope.scope());
        };
    }

    private void send(final WebhookSettings settings, final WebhookSettings.Route route,
            final Map<String, String> values) {
        final URI url = settings.urlOf(route);
        final String body = payload(settings, route, values, this.clock.instant());
        // One queue per webhook: its messages go out in order, one at a time.
        this.queues.compute(url, (key, previous) -> (previous == null
                ? CompletableFuture.<Void>completedFuture(null) : previous)
                .thenCompose(ignored -> Futures.logged(this.attempt(url, body, 1),
                        this.logger, "post to the webhook on " + url.getHost())
                        .exceptionally(failure -> null)));
    }

    private CompletableFuture<Void> attempt(final URI url, final String body, final int attempt) {
        final HttpClient http = this.client.get();
        if (http == null) {
            return CompletableFuture.completedFuture(null);
        }
        final HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "SakuraBans")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, failure) -> {
                    if (failure == null && response.statusCode() / 100 == 2) {
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    if (failure == null && response.statusCode() == 429) {
                        return this.later(retryAfter(response), url, body, attempt);
                    }
                    if (failure == null && response.statusCode() / 100 == 4) {
                        this.logger.warn("The webhook on {} refused a message ({}): {}",
                                url.getHost(), response.statusCode(), response.body());
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    if (attempt >= ATTEMPTS) {
                        this.logger.warn("Gave up posting to the webhook on {} after {}"
                                + " attempts ({})", url.getHost(), ATTEMPTS, failure != null
                                ? Futures.unwrap(failure).toString()
                                : "status " + response.statusCode());
                        return CompletableFuture.<Void>completedFuture(null);
                    }
                    return this.later(FIRST_BACKOFF.multipliedBy(1L << (attempt - 1)), url, body,
                            attempt);
                })
                .thenCompose(next -> next);
    }

    private CompletableFuture<Void> later(final Duration delay, final URI url, final String body,
            final int attempt) {
        final CompletableFuture<Void> done = new CompletableFuture<>();
        this.executors.schedule(delay, () -> this.attempt(url, body, attempt + 1)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        done.completeExceptionally(failure);
                    } else {
                        done.complete(null);
                    }
                }));
        return done;
    }

    static Duration retryAfter(final HttpResponse<String> response) {
        final String header = response.headers().firstValue("Retry-After").orElse(null);
        String seconds = header;
        if (seconds == null) {
            final Matcher matcher = RETRY_AFTER.matcher(response.body());
            seconds = matcher.find() ? matcher.group(1) : "1";
        }
        try {
            final double value = Double.parseDouble(seconds);
            return Duration.ofMillis(Math.max(0L, Math.min(60_000L, (long) (value * 1000))));
        } catch (final NumberFormatException e) {
            return FIRST_BACKOFF;
        }
    }

    static String payload(final WebhookSettings settings, final WebhookSettings.Route route,
            final Map<String, String> values, final Instant at) {
        final StringBuilder json = new StringBuilder("{");
        json.append("\"username\":").append(quote(settings.username(), 80));
        if (settings.avatarUrl() != null) {
            json.append(",\"avatar_url\":")
                    .append(quote(settings.avatarUrl().toString(), 2048));
        }
        json.append(",\"embeds\":[{");
        json.append("\"title\":").append(quote(fill(route.title(), values), 256));
        json.append(",\"description\":").append(quote(fill(route.description(), values), 4096));
        json.append(",\"color\":").append(route.color());
        json.append(",\"timestamp\":").append(quote(at.toString(), 64));
        json.append(",\"fields\":[");
        int count = 0;
        for (final WebhookSettings.Field field : route.fields()) {
            final String value = fill(field.value(), values);
            if (count == 25 || value.isBlank()) {
                continue;
            }
            if (count++ > 0) {
                json.append(',');
            }
            json.append("{\"name\":").append(quote(field.name(), 256))
                    .append(",\"value\":").append(quote(value, 1024))
                    .append(",\"inline\":true}");
        }
        return json.append("]}]}").toString();
    }

    private static String fill(final String text, final Map<String, String> values) {
        return ConsoleActions.fill(text, values);
    }

    static String quote(final String text, final int limit) {
        final String cut = text.length() > limit ? text.substring(0, limit - 1) + "…" : text;
        final StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < cut.length(); i++) {
            final char c = cut.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

}
