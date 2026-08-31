package com.sogeco.fleet.modules.mission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Genere chaque jour ouvre la mission d'une livraison recurrente
 * (MissionAutomation), a 9h30 — assez tot pour laisser le temps de
 * l'annuler avant un depart, assez tard pour eviter la nuit.
 * "Chaque jour" signifie du lundi au samedi : jamais le dimanche.
 *
 * Meme logique que LaverieScheduler pour le lavage hebdomadaire : passe
 * directement par MissionService.generateAutomatedMission (pas de
 * MISSION_CREATE en contexte, aucun utilisateur authentifie ici).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissionAutomationScheduler {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private final MissionAutomationRepository automationRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;

    // Jour de la semaine dans l'expression cron (MON-SAT) : garde-fou redondant ci-dessous
    // au cas ou la methode serait un jour declenchee autrement qu'a travers ce planning.
    @Scheduled(cron = "0 30 9 * * MON-SAT", zone = "Africa/Douala")
    @Transactional
    public void genererLivraisonsDuJour() {
        LocalDate today = LocalDate.now(ZONE);
        if (today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            return;
        }
        Instant startOfDay = today.atStartOfDay(ZONE).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(ZONE).toInstant();

        int created = 0;
        for (MissionAutomation automation : automationRepository.findByActiveTrueOrderByIdDesc()) {
            if (missionRepository.existsByMissionAutomationIdAndCreatedAtBetween(
                    automation.getId(), startOfDay, endOfDay)) {
                continue;
            }
            missionService.generateAutomatedMission(automation);
            created++;
        }

        if (created > 0) {
            log.info("{} livraison(s) automatisee(s) generee(s) pour le {}", created, today);
        }
    }
}
