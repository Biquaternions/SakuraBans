package net.serlith.sakurabans.api.template;

/**
 * How a template's mute notice or warning reaches a punished player who stays online. Bans and
 * kicks disconnect, so their screens are unaffected; so is the line a muted player gets each
 * time chat or a blocked command is refused, which is always chat.
 */
public enum TemplateDisplay {

    /** A chat message. The default. */
    CHAT,

    /**
     * A modal dialog with the message and a button to close it. Where the platform can't show
     * one (an older client, a proxy without packetevents), the message is sent as chat.
     */
    DIALOG
}
