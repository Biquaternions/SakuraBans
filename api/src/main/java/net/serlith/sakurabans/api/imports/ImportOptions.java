package net.serlith.sakurabans.api.imports;

import java.util.List;
import java.util.Objects;
import net.serlith.sakurabans.api.punish.Enforcement;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.FieldError.Code;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.api.validation.Validator;

/**
 * How an import runs. Build with {@link #builder()}.
 */
public interface ImportOptions {

    /**
     * Whether to only check and count, without storing anything.
     *
     * @return {@code false} by default
     */
    boolean dryRun();

    /**
     * Whether imported active punishments take effect right away.
     *
     * @return {@link Enforcement#NONE} by default (they apply from the next check)
     */
    Enforcement enforcement();

    /**
     * How many punishments are stored per transaction.
     *
     * @return 1 to 10,000; 500 by default
     */
    int batchSize();

    /**
     * The source tag stored on every imported punishment.
     *
     * @return the source; {@code import} by default
     */
    String source();

    /**
     * Starts with the default options.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builds {@link ImportOptions}. Not thread-safe.
     */
    final class Builder {

        private boolean dryRun;
        private Enforcement enforcement = Enforcement.NONE;
        private int batchSize = 500;
        private String source = "import";

        private Builder() {
        }

        /**
         * Sets whether to only check and count.
         *
         * @param dryRun {@code true} for a dry run
         * @return this builder
         */
        public Builder dryRun(final boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        /**
         * Sets the enforcement of imported active punishments.
         *
         * @param enforcement the enforcement
         * @return this builder
         */
        public Builder enforcement(final Enforcement enforcement) {
            this.enforcement = Objects.requireNonNull(enforcement, "enforcement");
            return this;
        }

        /**
         * Sets the batch size.
         *
         * @param batchSize 1 to 10,000
         * @return this builder
         */
        public Builder batchSize(final int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * Sets the source tag.
         *
         * @param source the source, for example {@code import:hydra}
         * @return this builder
         */
        public Builder source(final String source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        /**
         * Checks the values without building.
         *
         * @return every problem: {@code batchSize} out of range, {@code source} blank or too
         *         long
         */
        public List<FieldError> validate() {
            return Validator.create()
                    .check(this.batchSize >= 1 && this.batchSize <= 10_000, "batchSize",
                            Code.OUT_OF_RANGE, "must be 1-10000: " + this.batchSize)
                    .name("source", this.source, 64)
                    .errors();
        }

        /**
         * Builds the options.
         *
         * @return the options
         * @throws ValidationException with every problem {@link #validate()} reports
         */
        public ImportOptions build() {
            record Impl(boolean dryRun, Enforcement enforcement, int batchSize, String source)
                    implements ImportOptions {
            }

            ValidationException.throwIfAny(this.validate());
            return new Impl(this.dryRun, this.enforcement, this.batchSize, this.source);
        }
    }
}
