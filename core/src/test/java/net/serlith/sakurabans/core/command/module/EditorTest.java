package net.serlith.sakurabans.core.command.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.ServiceTest;
import net.serlith.sakurabans.core.admin.EditorSeal;
import net.serlith.sakurabans.core.admin.FakeEditorSite;
import net.serlith.sakurabans.core.command.CommandTest;
import net.serlith.sakurabans.core.command.FakeCommandActor;
import net.serlith.sakurabans.core.fake.FakeOnlinePlayer;
import net.serlith.sakurabans.core.model.TemplateBuilder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;

/** The editor commands against an in-memory editor site, the test acting as the browser. */
abstract class EditorTest extends CommandTest {

    static final String TOKEN = "t0p-s3cret-token";
    private static final Pattern LINK =
            Pattern.compile("/editor/([A-Za-z0-9_-]{22})#([A-Za-z0-9_-]{43})");

    protected final FakeEditorSite site = new FakeEditorSite().token(TOKEN);
    private final AtomicLong sequence = new AtomicLong(1795651260000L);

    @Override
    protected String configYaml() {
        return "web:\n  url: '" + this.site.url() + "/'\n  token: " + TOKEN + "\n";
    }

    @AfterEach
    final void closeSite() {
        this.site.close();
    }

    /** An open session, as the browser sees it. */
    record Session(String id, byte[] key, JsonObject payload) {
    }

    final Session open(final FakeCommandActor actor, final String arguments) {
        actor.clear();
        this.run(actor, ("sb editor " + arguments).strip());
        final Matcher link = LINK.matcher(String.join("\n", actor.texts()));
        Assertions.assertThat(link.find()).as(actor.texts().toString()).isTrue();
        final byte[] key = Base64.getUrlDecoder().decode(link.group(2));
        return new Session(link.group(1), key, JsonParser.parseString(
                this.opened(key, EditorSeal.Role.PAYLOAD, this.site.payload(link.group(1))))
                .getAsJsonObject());
    }

    final String opened(final byte[] key, final EditorSeal.Role role, final byte[] blob) {
        try {
            return EditorSeal.open(key, role, blob);
        } catch (final EditorSeal.SealException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * What the browser does on save: seals the change set and gets a code. A {@code sequence}
     * larger than the last one goes in when the text has none.
     */
    final String save(final Session session, final String changes) {
        final String json = changes.contains("\"sequence\"") ? changes
                : changes.replaceFirst("\\{", "{\"sequence\": "
                        + this.sequence.incrementAndGet() + ", ");
        return this.site.saveChanges(session.id(), new EditorSeal(new SecureRandom())
                .seal(session.key(), EditorSeal.Role.CHANGES, json));
    }

    /** The payload the site holds for the session now (the latest snapshot). */
    final JsonObject current(final Session session) {
        return JsonParser.parseString(this.opened(session.key(), EditorSeal.Role.PAYLOAD,
                this.site.payload(session.id()))).getAsJsonObject();
    }

    /** The result the plugin posted for a code. */
    final JsonObject result(final Session session, final String code) {
        final byte[] blob = this.site.result(code);
        Assertions.assertThat(blob).as("a result for " + code).isNotNull();
        return JsonParser.parseString(this.opened(session.key(), EditorSeal.Role.RESULT, blob))
                .getAsJsonObject();
    }

    final List<String> apply(final FakeCommandActor actor, final String code) {
        actor.clear();
        this.run(actor, "sb applyedits " + code);
        return lines(actor);
    }

    /** The lines without the prefix. */
    static List<String> lines(final FakeCommandActor actor) {
        return strip(actor.texts());
    }

    static List<String> lines(final FakeOnlinePlayer player) {
        return strip(player.messages().stream().map(ServiceTest::plain).toList());
    }

    static List<String> strip(final List<String> lines) {
        return lines.stream().map(line -> line.replaceFirst("^SakuraBans \\.{3}🖉 ", ""))
                .toList();
    }

    static TemplateInfo template(final String name, final String reason) {
        return TemplateBuilder.preset(name, PunishmentType.MUTE).reason(reason).build();
    }

    final long version(final String template) {
        return this.templates.find(template).orElseThrow().version();
    }
}
