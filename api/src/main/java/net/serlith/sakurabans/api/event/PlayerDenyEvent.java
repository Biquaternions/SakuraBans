package net.serlith.sakurabans.api.event;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serlith.sakurabans.api.model.Punishment;

/**
 * Before SakuraBans denies a player something: joining, switching servers, chatting or running a
 * command. Cancelling lets the player through, for example because another plugin denies it with
 * its own screen. Changing {@link #message(Component)} replaces what the player sees.
 *
 * <p>Runs on the thread that checks the player: the asynchronous login thread for logins, the
 * proxy's event thread for server switches, and the chat or command thread for mutes. Handlers
 * must be quick and must not block.
 */
public interface PlayerDenyEvent extends Cancellable {

    /**
     * The player.
     *
     * @return the account's UUID
     */
    UUID uuid();

    /**
     * The player's name.
     *
     * @return the name
     */
    String name();

    /**
     * The address the player connects from.
     *
     * @return the address
     */
    InetAddress address();

    /**
     * What is denied.
     *
     * @return the kind of denial
     */
    Denial denial();

    /**
     * The punishment that causes it. Empty for a lockdown or a failed check.
     *
     * @return the punishment, or empty
     */
    Optional<Punishment> punishment();

    /**
     * What the player sees: the disconnect screen for joins and switches, the chat message for
     * mutes.
     *
     * @return the message
     */
    Component message();

    /**
     * Replaces what the player sees.
     *
     * @param message the new message
     */
    void message(Component message);

    /**
     * What is denied.
     */
    enum Denial {

        /** Joining, because of a ban. */
        LOGIN_BANNED,

        /**
         * Joining, because the ban check timed out or failed ({@code login-check-timeout}). There
         * is no punishment; cancelling lets the player in unchecked.
         */
        LOGIN_CHECK_FAILED,

        /** Joining, because of a lockdown. */
        LOGIN_LOCKDOWN,

        /** Switching to a server (proxy only), because of a ban in that server's scope. */
        SERVER_SWITCH_BANNED,

        /** A proxy keeps a player from a server whose scope is locked down. */
        SERVER_SWITCH_LOCKDOWN,

        /**
         * Switching to a server (proxy only), because the ban check timed out or failed. There is
         * no punishment; cancelling lets the switch through unchecked.
         */
        SERVER_SWITCH_CHECK_FAILED,

        /** Chatting, because of a mute. */
        CHAT_MUTED,

        /**
         * Chatting under a shadow mute: the message is shown to the sender only, and
         * {@link PlayerDenyEvent#message()} is {@code Component.empty()}. Cancelling delivers it
         * normally.
         */
        CHAT_SHADOW_MUTED,

        /** Running a blacklisted command, because of a mute. */
        COMMAND_MUTED
    }
}
