package net.serlith.sakurabans.core.platform;

public enum PlatformType {
    PAPER(false),
    VELOCITY(true);

    private final boolean proxy;

    PlatformType(final boolean proxy) {
        this.proxy = proxy;
    }

    public boolean proxy() {
        return this.proxy;
    }
}
