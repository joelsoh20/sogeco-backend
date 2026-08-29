package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.NotificationChannel;
import com.sogeco.fleet.common.enums.ReportFormat;
import com.sogeco.fleet.common.enums.ReportType;
import com.sogeco.fleet.modules.alert.Notification;
import com.sogeco.fleet.modules.alert.NotificationRepository;
import com.sogeco.fleet.modules.role.Role;
import com.sogeco.fleet.modules.role.RoleRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generation mensuelle automatique.
 *
 * Le rapport est genere et stocke, puis une notification IN_APP est
 * creee pour chaque utilisateur Direction ou Administrateur : ils la
 * verront a leur prochaine connexion. L'envoi par courriel reel
 * exigerait une configuration SMTP qui n'existe nulle part dans ce
 * projet — le canal EMAIL de l'entite Notification existe deja pour
 * ce jour-la, mais rien ne l'envoie encore. Le perimetre actuel
 * s'arrete donc a l'affichage dans l'application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportScheduler {

    private final ReportExportService exportService;
    private final NotificationRepository notificationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SettingService settingService;

    /** Le 1er de chaque mois a 5h, pour le mois civil qui vient de se terminer. */
    @Scheduled(cron = "0 0 5 1 * *", zone = "Africa/Douala")
    @Transactional
    public void generateMonthlySynthesis() {
        if (!settingService.getBoolean("reports.monthly_auto_generate", true)) {
            return;
        }

        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        LocalDate from = previousMonth.atDay(1);
        LocalDate to = previousMonth.atEndOfMonth();

        GeneratedReport report = exportService.exportScheduled(
                ReportType.SYNTHESE_MENSUELLE, ReportFormat.XLSX, from, to);

        notifyRecipients(report, previousMonth);

        log.info("Synthese mensuelle {} generee automatiquement : {} lignes",
                previousMonth, report.getRowCount());
    }

    private void notifyRecipients(GeneratedReport report, YearMonth month) {
        Set<User> recipients = new LinkedHashSet<>();
        roleRepository.findByCode(Role.ADMIN).ifPresent(role -> recipients.addAll(userRepository.findByRolesContaining(role)));

        for (User user : recipients) {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .channel(NotificationChannel.IN_APP)
                    .title("Synthese mensuelle disponible")
                    .message("Le rapport de rentabilite de %s est pret.".formatted(month))
                    .sentAt(Instant.now())
                    .status(com.sogeco.fleet.common.enums.NotificationStatus.ENVOYEE)
                    .build());
        }

        log.info("{} destinataire(s) notifie(s) pour la synthese {}", recipients.size(), month);
    }
}
