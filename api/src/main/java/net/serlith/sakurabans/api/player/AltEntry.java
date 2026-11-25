package net.serlith.sakurabans.api.player;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.PlayerProfileView;
import net.serlith.sakurabans.api.model.PunishmentType;

/**
 * An account linked to another one through shared addresses. Implemented by SakuraBans; don't
 * implement it.
 */
public interface AltEntry {

    /**
     * The linked account.
     *
     * @return the account
     */
    PlayerProfileView account();

    /**
     * The addresses that link the two accounts (for {@link AddressStrictness#STERN}, the
     * addresses of the intermediate account).
     *
     * @return the addresses
     */
    List<InetAddress> sharedAddresses();

    /**
     * The types of punishment the linked account has active, in any scope.
     *
     * @return the types, empty if it has none
     */
    Set<PunishmentType> activePunishments();
}
