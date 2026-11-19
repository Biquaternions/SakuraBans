package net.serlith.sakurabans.api.settings;

import java.util.Map;
import net.serlith.sakurabans.api.admin.Versioned;

/**
 * The effective settings at one version. Implemented by SakuraBans; don't implement it.
 */
public interface SettingsSnapshot extends Versioned {

    /**
     * Every setting's effective value.
     *
     * @return the values by key
     */
    Map<String, Object> values();

    /**
     * Where each effective value comes from.
     *
     * @return the origins by key
     */
    Map<String, ValueOrigin> origins();
}
