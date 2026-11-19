package net.serlith.sakurabans.core.configuration;

public record ConfigIssue(String file, String path, String message, Severity severity) {

    public enum Severity { ERROR, WARNING }

    public static ConfigIssue error(final String file, final String path, final String message) {
        return new ConfigIssue(file, path, message, Severity.ERROR);
    }

    public static ConfigIssue warning(final String file, final String path, final String message) {
        return new ConfigIssue(file, path, message, Severity.WARNING);
    }

    @Override
    public String toString() {
        return this.file + (this.path.isEmpty() ? "" : ": " + this.path) + ": " + this.message;
    }
}
