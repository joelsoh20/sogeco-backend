package com.sogeco.fleet.modules.tracking.dto;

/** Legende de la carte GPS : compteurs par statut. */
public record TrackingStatsResponse(
        long enMouvement,
        long aLArret,
        long horsLigne,
        long enMaintenance,
        long total
) {
}
