package net.serlith.sakurabans.core.storage;

import java.util.Optional;

/** A database the storage tests can run against (S16). */
public interface TestDatabase {

    /** The default: a new H2 file in the test's temporary directory. */
    TestDatabase H2 = new TestDatabase() {
        @Override
        public Optional<String> freshStorageYaml() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "H2";
        }
    };

    /**
     * A {@code storage.yml} pointing at a new, empty database (or schema); empty to keep the
     * default H2 file.
     */
    Optional<String> freshStorageYaml();
}
