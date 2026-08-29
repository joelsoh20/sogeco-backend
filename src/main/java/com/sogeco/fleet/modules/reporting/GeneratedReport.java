package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.ReportFormat;
import com.sogeco.fleet.common.enums.ReportType;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Trace d'un rapport genere.
 *
 * Sert deux usages : l'historique de ce que l'utilisateur a deja
 * exporte, et le point d'ancrage de la generation periodique — la
 * tache planifiee cree une ligne ici, que l'ecran Rapports peut
 * lister sans regenerer le fichier.
 */
@Entity
@Table(name = "generated_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 10)
    private ReportFormat format;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Builder.Default
    @Column(name = "row_count", nullable = false)
    private Integer rowCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id")
    private User requestedBy;

    @Builder.Default
    @Column(name = "is_scheduled", nullable = false)
    private Boolean isScheduled = false;

    @Builder.Default
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();
}
