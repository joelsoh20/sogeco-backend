package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.DriverStatus;
import com.sogeco.fleet.common.enums.RatingClass;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Chauffeur.
 *
 * Le compte applicatif (user) est facultatif : la decision D3 ouvre un
 * acces en consultation, mais tous les chauffeurs n'en auront pas.
 */
@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Driver extends SoftDeletableEntity {

    @Column(name = "matricule", nullable = false, length = 30, unique = true)
    private String matricule;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "job_title", length = 80)
    private String jobTitle;

    // ---- Permis ----

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "license_category", length = 20)
    private String licenseCategory;

    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    @Column(name = "monthly_salary", precision = 15, scale = 2)
    private BigDecimal monthlySalary;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverStatus status = DriverStatus.ACTIF;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // ---- Compteurs denormalises ----

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Builder.Default
    @Column(name = "incidents_count", nullable = false)
    private Integer incidentsCount = 0;

    @Builder.Default
    @Column(name = "total_missions", nullable = false)
    private Integer totalMissions = 0;

    @Builder.Default
    @Column(name = "total_kilometers", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalKilometers = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_document_id")
    private Document photo;

    // ------------------------------------------------------------------
    // Comportement
    // ------------------------------------------------------------------

    public String getFullName() {
        return firstName + " " + lastName;
    }

    /** Anciennete affichee sur la fiche : "3 ans 2 mois". */
    public String getSeniorityLabel() {
        if (hireDate == null) {
            return null;
        }
        Period period = Period.between(hireDate, LocalDate.now());
        if (period.getYears() == 0) {
            return period.getMonths() + " mois";
        }
        return "%d an%s %d mois".formatted(
                period.getYears(), period.getYears() > 1 ? "s" : "", period.getMonths());
    }

    public Integer getSeniorityMonths() {
        return hireDate == null ? null : (int) ChronoUnit.MONTHS.between(hireDate, LocalDate.now());
    }

    public Long licenseDaysRemaining() {
        return licenseExpiryDate == null
                ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), licenseExpiryDate);
    }

    /** Informatif uniquement : n'interdit plus l'affectation, voir isAssignable(). */
    public boolean isLicenseExpired() {
        return licenseExpiryDate != null && licenseExpiryDate.isBefore(LocalDate.now());
    }

    public boolean isAssignable() {
        return Boolean.TRUE.equals(getActive()) && status.isAssignable();
    }

    public RatingClass getRatingClass() {
        return RatingClass.of(performanceScore);
    }
}
