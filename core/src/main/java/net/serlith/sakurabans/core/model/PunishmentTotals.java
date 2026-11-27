package net.serlith.sakurabans.core.model;

public record PunishmentTotals(long activeBans, long activeMutes, long activeWarns,
        long totalBans, long totalMutes, long totalWarns, long totalKicks) {
}
