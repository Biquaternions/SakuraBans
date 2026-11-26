package net.serlith.sakurabans.api.model;

import java.util.Objects;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * A link to evidence for a punishment, such as a screenshot or a clip. SakuraBans stores the
 * link only; it never fetches it.
 */
public interface EvidenceRef {

    /**
     * The link.
     *
     * @return an {@code http} or {@code https} URL
     */
    String uri();

    /**
     * Creates a reference.
     *
     * @param uri an {@code http} or {@code https} URL of at most {@link Lengths#MAX_EVIDENCE}
     *         characters
     * @return the reference
     */
    static EvidenceRef of(final String uri) {
        record Impl(String uri) implements EvidenceRef {
            Impl {
                Objects.requireNonNull(uri, "uri");
                Checks.text(uri, Lengths.MAX_EVIDENCE, "uri");
                if (!Checks.EVIDENCE_URL.matcher(uri).matches()) {
                    throw Checks.error("uri", FieldError.Code.INVALID_FORMAT,
                            "must be an http(s) URL: '" + uri + "'");
                }
            }
        }

        return new Impl(uri);
    }
}
