package net.serlith.sakurabans.core.configuration.settings;

public enum StorageType {

    H2(0), MARIADB(3306), MYSQL(3306), POSTGRESQL(5432);

    private final int defaultPort;

    StorageType(final int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return this.defaultPort;
    }
}
