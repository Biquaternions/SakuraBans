package net.serlith.sakurabans.core.permission;

public enum Decision {
    ALLOWED,
    NO_PERMISSION,
    EXEMPT;

    public boolean allowed() {
        return this == ALLOWED;
    }
}
