package net.serlith.sakurabans.api.imports;

import java.util.List;
import java.util.Optional;
import net.serlith.sakurabans.api.validation.FieldError;

/**
 * What an import did. Implemented by SakuraBans; don't implement it.
 */
public interface ImportReport {

    /**
     * Whether it was a dry run (nothing stored).
     *
     * @return {@code true} for a dry run
     */
    boolean dryRun();

    /**
     * How many punishments were imported (or would be, in a dry run).
     *
     * @return the count
     */
    long imported();

    /**
     * How many were skipped because their external id was already imported.
     *
     * @return the count
     */
    long skipped();

    /**
     * How many punishments couldn't be imported, also those beyond {@link #failures()}.
     *
     * @return the count
     */
    long failed();

    /**
     * The punishments that couldn't be imported, the first 50 of them.
     *
     * @return the failures, in input order
     */
    List<Failure> failures();

    /**
     * One punishment that couldn't be imported.
     */
    interface Failure {

        /**
         * Its position in the input.
         *
         * @return the index, from 0
         */
        long index();

        /**
         * Its id in the other system.
         *
         * @return the external id, or empty
         */
        Optional<String> externalId();

        /**
         * What is wrong with it (an unknown kind, an invalid victim, ...).
         *
         * @return the errors, never empty
         */
        List<FieldError> errors();
    }
}
