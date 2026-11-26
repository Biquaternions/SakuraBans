package net.serlith.sakurabans.core.enforce;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public record SignedChatPolicy(boolean signedVelocity) {

    public boolean chatCancellable() {
        return this.signedVelocity;
    }

    public boolean commandCancellable(final boolean signedArguments) {
        return this.signedVelocity || !signedArguments;
    }

    public void warnIfMissing(final ComponentLogger logger) {
        if (this.signedVelocity) {
            return;
        }
        logger.warn("SignedVelocity isn't installed, so mutes can't block chat: denying signed"
                + " chat would kick the player. Until it is, muted players can still chat and use"
                + " blacklisted commands that carry signed arguments (other blacklisted commands"
                + " are still blocked). Install SignedVelocity on this proxy and its backend"
                + " plugin on every server");
    }
}
