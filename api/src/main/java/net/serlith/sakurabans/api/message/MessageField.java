package net.serlith.sakurabans.api.message;

import java.util.List;

/**
 * One message in the schema. Implemented by SakuraBans; don't implement it.
 */
public interface MessageField {

    /**
     * The message key.
     *
     * @return the key, for example {@code punish.ban.screen}
     */
    String key();

    /**
     * The placeholders the message may use, without angle brackets.
     *
     * @return the placeholders, for example {@code reason}, {@code expires}
     */
    List<String> placeholders();

    /**
     * The text SakuraBans ships, in English.
     *
     * @return the default MiniMessage text
     */
    String defaultText();

    /**
     * Where the message is shown, in English.
     *
     * @return the description
     */
    String description();
}
