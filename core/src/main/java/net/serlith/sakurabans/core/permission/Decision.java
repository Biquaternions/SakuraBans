package net.serlith.sakurabans.core.permission;

public enum Decision {
    ALLOWED,
    NO_PERMISSION,
    EXEMPT,
    TEMPLATE_PROTECTED;

    public boolean allowed() {
        return this == ALLOWED;
    }
}
