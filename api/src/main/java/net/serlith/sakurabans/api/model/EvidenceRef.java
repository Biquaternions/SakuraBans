package net.serlith.sakurabans.api.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * A pointer to evidence for a punishment: a web URL, or a reference into another plugin's data
 * in the form {@code <namespace>:<type>:<id>}, for example {@code hydra:chat-snapshot:123}.
 * SakuraBans stores the reference only; it never fetches it.
 */
public interface EvidenceRef {

    /**
     * The reference.
     *
     * @return the URL or {@code <namespace>:<type>:<id>}
     */
    String uri();

    /**
     * The type of a namespaced reference, which templates use to require evidence.
     *
     * @return {@code <namespace>:<type>} (for example {@code hydra:chat-snapshot}), or empty for
     *         a URL
     */
    default Optional<String> type() {
        final Matcher matcher = Checks.EVIDENCE_REFERENCE.matcher(this.uri());
        return matcher.matches()
                ? Optional.of(matcher.group(1) + ":" + matcher.group(2))
                : Optional.empty();
    }

    /**
     * Creates a reference.
     *
     * @param uri an {@code http} or {@code https} URL, or {@code <namespace>:<type>:<id>} where
     *         namespace and type use {@code a-z}, {@code 0-9}, {@code _}, {@code -} and the id
     *         also letters in any case and {@code .}; at most {@link Lengths#MAX_EVIDENCE}
     *         characters
     * @return the reference
     */
    static EvidenceRef of(final String uri) {
        record Impl(String uri) implements EvidenceRef {
            Impl {
                Objects.requireNonNull(uri, "uri");
                Checks.text(uri, Lengths.MAX_EVIDENCE, "uri");
                if (!Checks.EVIDENCE_URL.matcher(uri).matches()
                        && !Checks.EVIDENCE_REFERENCE.matcher(uri).matches()) {
                    throw Checks.error("uri", FieldError.Code.INVALID_FORMAT,
                            "must be an http(s) URL or <namespace>:<type>:<id>: '" + uri + "'");
                }
            }
        }

        return new Impl(uri);
    }
}
