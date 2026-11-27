package net.serlith.sakurabans.core.enforce;

import net.serlith.sakurabans.core.configuration.ConfigService;

public final class CommandBlacklist {

    private final ConfigService config;

    public CommandBlacklist(final ConfigService config) {
        this.config = config;
    }

    public boolean blocked(final String commandLine) {
        String line = commandLine.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        final int space = line.indexOf(' ');
        final String label = space < 0 ? line : line.substring(0, space);
        return !label.isEmpty()
                && this.config.settings().enforcement().muteCommandBlacklist().matches(label);
    }

}
