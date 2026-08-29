package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.DriverStatus;
import com.sogeco.fleet.common.enums.RatingClass;
import com.sogeco.fleet.modules.document.dto.DocumentResponse;
import com.sogeco.fleet.modules.driver.Driver;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Panneau de detail de la fiche chauffeur. */
public record DriverDetailResponse(
        Long id,
        String matricule,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        LocalDate birthDate,
        LocalDate hireDate,
        String seniorityLabel,
        Integer seniorityMonths,
        String jobTitle,
        String licenseNumber,
        String licenseCategory,
        LocalDate licenseExpiryDate,
        Long licenseDaysRemaining,
        Boolean licenseExpired,
        BigDecimal monthlySalary,
        DriverStatus status,
        Long cityId,
        String cityName,
        Long vehicleId,
        String registrationNumber,
        Integer totalMissions,
        BigDecimal totalKilometers,
        Integer incidentsCount,
        BigDecimal performanceScore,
        RatingClass ratingClass,
        List<RatingResponse> ratings,
        BigDecimal currentBonus,
        Boolean assignable,
        List<DocumentResponse> documents,
        Boolean active,
        Instant createdAt
) {
    public static DriverDetailResponse of(Driver driver,
                                          Long vehicleId, String registrationNumber,
                                          List<RatingResponse> ratings,
                                          BigDecimal currentBonus,
                                          List<DocumentResponse> documents,
                                          boolean includeSalary) {
        return new DriverDetailResponse(
                driver.getId(),
                driver.getMatricule(),
                driver.getFirstName(),
                driver.getLastName(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getBirthDate(),
                driver.getHireDate(),
                driver.getSeniorityLabel(),
                driver.getSeniorityMonths(),
                driver.getJobTitle(),
                driver.getLicenseNumber(),
                driver.getLicenseCategory(),
                driver.getLicenseExpiryDate(),
                driver.licenseDaysRemaining(),
                driver.isLicenseExpired(),
                // Salaire et prime : visibles des seuls roles habilites (RG-9.13)
                includeSalary ? driver.getMonthlySalary() : null,
                driver.getStatus(),
                driver.getCity() == null ? null : driver.getCity().getId(),
                driver.getCity() == null ? null : driver.getCity().getName(),
                vehicleId,
                registrationNumber,
                driver.getTotalMissions(),
                driver.getTotalKilometers(),
                driver.getIncidentsCount(),
                driver.getPerformanceScore(),
                driver.getRatingClass(),
                ratings,
                includeSalary ? currentBonus : null,
                driver.isAssignable(),
                documents,
                driver.getActive(),
                driver.getCreatedAt());
    }
}
