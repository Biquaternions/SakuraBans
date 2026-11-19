package net.serlith.sakurabans.api.integration;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.Validator;

/**
 * An alt found by an {@link AltSource}.
 */
public interface ExternalAlt {

    /**
     * The linked account.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * How sure the source is that the accounts belong to the same person.
     *
     * @return the score, from 0 to 1
     */
    double score();

    /**
     * The addresses that link the accounts, if the source knows them.
     *
     * @return the addresses, possibly empty
     */
    List<InetAddress> sharedAddresses();

    /**
     * Creates an alt.
     *
     * @param uuid the linked account
     * @param score the confidence, from 0 to 1
     * @param sharedAddresses the linking addresses, possibly empty
     * @return the alt
     * @throws net.serlith.sakurabans.api.validation.ValidationException if the score is outside
     *         0 to 1
     */
    static ExternalAlt of(
            final UUID uuid, final double score, final List<InetAddress> sharedAddresses) {
        record Impl(UUID uuid, double score, List<InetAddress> sharedAddresses)
                implements ExternalAlt {
        }

        Objects.requireNonNull(uuid, "uuid");
        Validator.create().check(score >= 0 && score <= 1, "score", Code.OUT_OF_RANGE,
                "must be from 0 to 1: " + score).throwIfAny();
        return new Impl(uuid, score, List.copyOf(sharedAddresses));
    }
}
