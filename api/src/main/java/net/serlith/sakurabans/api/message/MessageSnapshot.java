package net.serlith.sakurabans.api.message;

import java.util.Locale;
import java.util.Map;
import net.serlith.sakurabans.api.admin.Versioned;
import net.serlith.sakurabans.api.settings.ValueOrigin;

/**
 * The effective messages of one locale at one version. Implemented by SakuraBans; don't
 * implement it.
 */
public interface MessageSnapshot extends Versioned {

    /**
     * The locale.
     *
     * @return the locale
     */
    Locale locale();

    /**
     * Every message's effective MiniMessage text.
     *
     * @return the texts by key
     */
    Map<String, String> texts();

    /**
     * Where each effective text comes from.
     *
     * @return the origins by key
     */
    Map<String, ValueOrigin> origins();
}
