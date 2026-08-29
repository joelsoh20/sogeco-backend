package com.sogeco.fleet.modules.mission.dto;

import java.math.BigDecimal;

/** Compteurs de tete de l'ecran Missions et Livraisons. */
public record MissionStatsResponse(
        long total,
        long terminees,
        long enCours,
        long enAttente,
        long annulees,
        BigDecimal tauxCompletion,
        BigDecimal tauxAnnulation,
        BigDecimal chiffreAffaires,
        BigDecimal kilometresParcourus,
        long missionsSansChiffreAffaires
) {
}
