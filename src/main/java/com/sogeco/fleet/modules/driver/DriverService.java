package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.DriverStatus;
import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.common.enums.RatingClass;
import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.document.DocumentService;
import com.sogeco.fleet.modules.driver.dto.*;
import com.sogeco.fleet.modules.maintenance.MaintenanceLogRepository;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.role.Role;
import com.sogeco.fleet.modules.role.RoleRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.VehicleAssignment;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository repository;
    private final DriverActionRepository actionRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final DriverPerformanceService performanceService;
    private final AuditService auditService;
    private final SettingService settingService;
    private final MissionRepository missionRepository;
    private final MaintenanceLogRepository maintenanceRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public PageResponse<DriverResponse> list(Pageable pageable) {
        Map<Long, VehicleAssignment> byDriver = activeAssignmentsByDriver();
        boolean salary = canSeeSalary();

        // Un gestionnaire non-administrateur ne voit que les chauffeurs de sa ville.
        Page<Driver> page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByCity_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));

        return PageResponse.from(page, driver -> {
            VehicleAssignment assignment = byDriver.get(driver.getId());
            return DriverResponse.from(driver,
                    assignment == null ? null : assignment.getVehicle().getId(),
                    assignment == null ? null : assignment.getVehicle().getRegistrationNumber(),
                    salary ? performanceService.currentBonusAmount(driver.getId()) : null);
        });
    }

    /** Classement de l'ecran Chauffeurs, du meilleur score au moins bon. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<DriverResponse> ranking() {
        Map<Long, VehicleAssignment> byDriver = activeAssignmentsByDriver();
        boolean salary = canSeeSalary();

        return repository.findByActiveTrueAndPerformanceScoreIsNotNullOrderByPerformanceScoreDesc()
                .stream()
                .map(driver -> {
                    VehicleAssignment assignment = byDriver.get(driver.getId());
                    return DriverResponse.from(driver,
                            assignment == null ? null : assignment.getVehicle().getId(),
                            assignment == null ? null : assignment.getVehicle().getRegistrationNumber(),
                            salary ? performanceService.currentBonusAmount(driver.getId()) : null);
                })
                .toList();
    }

    /**
     * Classement "meilleur chauffeur" par livraisons et par fiabilite
     * (le moins de pannes), sur le semestre ecoule (6 mois pleins) — a
     * regrouper cote appelant par ville et par type d'usage du camion
     * actuellement affecte (tour de ville / voyage). Un chauffeur sans
     * camion affecte apparait avec usageType/vehicleId nuls.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<DriverSemesterRankingResponse> semesterRanking() {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        LocalDate today = LocalDate.now(zone);
        LocalDate sixMonthsAgo = today.minusMonths(6);
        Instant instantFrom = sixMonthsAgo.atStartOfDay(zone).toInstant();
        Instant instantTo = today.plusDays(1).atStartOfDay(zone).toInstant();

        Map<Long, Long> deliveries = new HashMap<>();
        for (Object[] row : missionRepository.countCompletedByDriver(instantFrom, instantTo)) {
            deliveries.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, Long> breakdowns = new HashMap<>();
        for (Object[] row : maintenanceRepository.countBreakdownsByDriver(sixMonthsAgo, today)) {
            breakdowns.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }

        Map<Long, VehicleAssignment> byDriver = activeAssignmentsByDriver();

        List<DriverSemesterRankingResponse> results = new ArrayList<>();
        for (Driver driver : repository.findByActiveTrueOrderByLastNameAsc()) {
            VehicleAssignment assignment = byDriver.get(driver.getId());
            City city = driver.getCity();

            results.add(new DriverSemesterRankingResponse(
                    driver.getId(), driver.getFullName(),
                    city == null ? null : city.getId(), city == null ? null : city.getName(),
                    assignment == null ? null : assignment.getVehicle().getId(),
                    assignment == null ? null : assignment.getVehicle().getRegistrationNumber(),
                    assignment == null ? null : assignment.getVehicle().getUsageType(),
                    deliveries.getOrDefault(driver.getId(), 0L),
                    breakdowns.getOrDefault(driver.getId(), 0L)));
        }
        return results;
    }

    /** Barre de recherche du tableau de bord — nom, prenom ou matricule. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<DriverResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        Map<Long, VehicleAssignment> byDriver = activeAssignmentsByDriver();
        boolean salary = canSeeSalary();

        return repository.search(q.trim(), org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .map(driver -> {
                    VehicleAssignment assignment = byDriver.get(driver.getId());
                    return DriverResponse.from(driver,
                            assignment == null ? null : assignment.getVehicle().getId(),
                            assignment == null ? null : assignment.getVehicle().getRegistrationNumber(),
                            salary ? performanceService.currentBonusAmount(driver.getId()) : null);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ') or hasAuthority('SELF_READ')")
    public DriverDetailResponse get(Long id) {
        Driver driver = find(id);
        assertCanView(driver);

        VehicleAssignment assignment = assignmentRepository.findByDriverIdAndEndDateIsNull(id).orElse(null);
        boolean salary = canSeeSalary() || isSelf(driver);

        return DriverDetailResponse.of(
                driver,
                assignment == null ? null : assignment.getVehicle().getId(),
                assignment == null ? null : assignment.getVehicle().getRegistrationNumber(),
                performanceService.ratingsOf(id),
                performanceService.currentBonusAmount(id),
                documentService.listFor(EntityType.DRIVER, id),
                salary);
    }

    /** Dossier du chauffeur connecte — point d'entree de l'espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public DriverDetailResponse me() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        Driver driver = repository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED",
                        "Aucun dossier chauffeur associe a ce compte", HttpStatus.FORBIDDEN));
        return get(driver.getId());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public DriverStatsResponse stats() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        for (RatingClass value : RatingClass.values()) {
            distribution.put(value.name(), 0L);
        }
        repository.findByActiveTrueAndPerformanceScoreIsNotNullOrderByPerformanceScoreDesc()
                .forEach(driver -> distribution.merge(driver.getRatingClass().name(), 1L, Long::sum));

        int warningDays = 30;
        long expiringLicenses = repository
                .findByActiveTrueAndLicenseExpiryDateLessThanEqual(LocalDate.now().plusDays(warningDays))
                .size();

        return new DriverStatsResponse(
                repository.countByActiveTrue(),
                repository.countByStatusAndActiveTrue(DriverStatus.ACTIF),
                repository.countByStatusAndActiveTrue(DriverStatus.EN_CONGE),
                repository.countByStatusAndActiveTrue(DriverStatus.SUSPENDU),
                repository.averagePerformanceScore(),
                repository.totalKilometers(),
                repository.totalIncidents() == null ? 0 : repository.totalIncidents(),
                expiringLicenses,
                distribution);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<DriverResponse> unassigned() {
        return repository.findUnassigned().stream()
                .map(driver -> DriverResponse.from(driver, null, null, null))
                .toList();
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_CREATE')")
    public DriverCreationResult create(DriverRequest request) {
        if (repository.existsByMatriculeIgnoreCase(request.matricule())) {
            throw new DuplicateResourceException("Chauffeur", "matricule", request.matricule());
        }

        User linkedUser = resolveUser(request.userId());
        String temporaryPassword = null;
        // accountEmail non nul (meme vide) signale une demande de creation de
        // compte ; une adresse vide se traduit par une connexion nom+prenom.
        if (linkedUser == null && request.accountEmail() != null) {
            temporaryPassword = generatePassword();
            linkedUser = createChauffeurAccount(request, temporaryPassword);
        }

        Driver driver = Driver.builder()
                .matricule(request.matricule().toUpperCase())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .birthDate(request.birthDate())
                .hireDate(request.hireDate())
                .jobTitle(request.jobTitle())
                .licenseNumber(request.licenseNumber())
                .licenseCategory(request.licenseCategory())
                .licenseExpiryDate(request.licenseExpiryDate())
                .monthlySalary(request.monthlySalary())
                .city(resolveCity(request.cityId()))
                .user(linkedUser)
                .status(DriverStatus.ACTIF)
                .build();

        Driver saved = repository.save(driver);
        log.info("Chauffeur {} cree par {}", saved.getFullName(), SecurityUtils.currentUserEmail());
        return new DriverCreationResult(get(saved.getId()), temporaryPassword);
    }

    /**
     * Compte de connexion cree a la volee, role Chauffeur uniquement.
     *
     * Ouvre la creation d'un chauffeur avec acces applicatif aux
     * detenteurs de DRIVER_CREATE (ex. Gestionnaire) sans leur accorder
     * USER_MANAGE : le role attribue est toujours Chauffeur, jamais
     * celui demande par l'appelant — il n'y a pas de champ role ici.
     */
    private User createChauffeurAccount(DriverRequest request, String rawPassword) {
        String email = request.accountEmail().isBlank()
                ? placeholderEmail(request.firstName(), request.lastName())
                : request.accountEmail().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Utilisateur", "email", email);
        }
        Role chauffeurRole = roleRepository.findByCode(Role.CHAUFFEUR)
                .orElseThrow(() -> new BusinessException("RG-13.3",
                        "Role Chauffeur introuvable", HttpStatus.INTERNAL_SERVER_ERROR));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .status(UserStatus.ACTIF)
                // Mot de passe defini par l'administrateur, valable sans
                // changement impose — c'est lui qui gere les mots de passe,
                // pas le chauffeur.
                .mustChangePassword(false)
                .roles(Set.of(chauffeurRole))
                .build();

        return userRepository.save(user);
    }

    /** 14 caracteres, sans les glyphes ambigus (O/0, l/1). */
    private String generatePassword() {
        StringBuilder builder = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * Adresse interne, jamais communiquee : sans email reel, le compte
     * se connecte par nom et prenom (AuthService.resolveUser()), qui ne
     * regarde jamais cette valeur.
     */
    private String placeholderEmail(String firstName, String lastName) {
        String base = (firstName + "." + lastName).toLowerCase()
                .replaceAll("[^a-z0-9.]", "").replaceAll("\\.+", ".");
        if (base.isBlank()) {
            base = "chauffeur";
        }
        String candidate = base + "@sogeco.local";
        int suffix = 2;
        while (userRepository.existsByEmailIgnoreCase(candidate)) {
            candidate = base + suffix + "@sogeco.local";
            suffix++;
        }
        return candidate;
    }

    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_UPDATE')")
    public DriverDetailResponse update(Long id, DriverRequest request) {
        Driver driver = find(id);

        EditWindowGuard.assertEditable(driver.getCreatedAt(),
                settingService.getInt("driver.edit_window_hours", 1), "RG-9-EDIT", "Ce chauffeur");

        repository.findByMatriculeIgnoreCase(request.matricule())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Chauffeur", "matricule", request.matricule());
                });

        driver.setMatricule(request.matricule().toUpperCase());
        driver.setFirstName(request.firstName());
        driver.setLastName(request.lastName());
        driver.setPhone(request.phone());
        driver.setBirthDate(request.birthDate());
        driver.setHireDate(request.hireDate());
        driver.setJobTitle(request.jobTitle());
        driver.setLicenseNumber(request.licenseNumber());
        driver.setLicenseCategory(request.licenseCategory());
        driver.setLicenseExpiryDate(request.licenseExpiryDate());
        driver.setMonthlySalary(request.monthlySalary());
        driver.setCity(resolveCity(request.cityId()));
        driver.setUser(resolveUser(request.userId()));

        return get(id);
    }

    /**
     * Changement de statut. Un chauffeur qui quitte l'etat actif voit
     * son affectation liberee : il ne peut plus conduire.
     */
    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_UPDATE')")
    public DriverDetailResponse changeStatus(Long id, DriverStatus status) {
        Driver driver = find(id);
        driver.setStatus(status);

        if (status != DriverStatus.ACTIF) {
            assignmentRepository.findByDriverIdAndEndDateIsNull(id).ifPresent(assignment -> {
                assignment.close(LocalDate.now());
                log.info("Affectation de {} liberee : passage au statut {}", driver.getFullName(), status);
            });
        }

        return get(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_DELETE')")
    public void deactivate(Long id) {
        Driver driver = find(id);

        assignmentRepository.findByDriverIdAndEndDateIsNull(id)
                .ifPresent(assignment -> assignment.close(LocalDate.now()));

        driver.setStatus(DriverStatus.SORTI);
        driver.deactivate();
    }

    // ------------------------------------------------------------------
    // Actions RH
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<DriverActionResponse> actionsOf(Long driverId) {
        return actionRepository.findByDriverIdOrderByActionDateDesc(driverId)
                .stream().map(DriverActionResponse::from).toList();
    }

    /**
     * Trace une action RH : avertissement, formation, entretien.
     * Un avertissement incremente le compteur d'incidents, qui pese
     * ensuite sur la note de conduite securisee.
     */
    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_UPDATE')")
    public DriverActionResponse addAction(Long driverId, DriverActionRequest request) {
        Driver driver = find(driverId);

        DriverAction action = DriverAction.builder()
                .driver(driver)
                .actionType(request.actionType())
                .actionDate(request.actionDate() == null ? LocalDate.now() : request.actionDate())
                .motif(request.motif())
                .comment(request.comment())
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        DriverAction saved = actionRepository.save(action);

        if (request.actionType() == com.sogeco.fleet.common.enums.DriverActionType.AVERTISSEMENT) {
            driver.setIncidentsCount(driver.getIncidentsCount() + 1);
        }

        log.info("Action {} enregistree pour {} par {}",
                request.actionType(), driver.getFullName(), SecurityUtils.currentUserEmail());

        return DriverActionResponse.from(saved);
    }

    // ------------------------------------------------------------------

    private Map<Long, VehicleAssignment> activeAssignmentsByDriver() {
        Map<Long, VehicleAssignment> map = new HashMap<>();
        assignmentRepository.findByEndDateIsNull()
                .forEach(assignment -> map.put(assignment.getDriver().getId(), assignment));
        return map;
    }

    /** Salaires et primes : roles habilites uniquement (RG-9.13). */
    private boolean canSeeSalary() {
        return SecurityUtils.hasPermission("SALARY_READ");
    }

    private boolean isSelf(Driver driver) {
        return driver.getUser() != null
                && SecurityUtils.currentUserId()
                    .map(id -> id.equals(driver.getUser().getId()))
                    .orElse(false);
    }

    /** Un chauffeur ne consulte que son propre dossier (decision D3). */
    private void assertCanView(Driver driver) {
        if (SecurityUtils.hasPermission("DRIVER_READ")) {
            return;
        }
        if (!isSelf(driver)) {
            throw new BusinessException("ACCESS_DENIED",
                    "Vous ne pouvez consulter que votre propre dossier", HttpStatus.FORBIDDEN);
        }
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", cityId));
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        repository.findByUserId(userId).ifPresent(existing -> {
            throw new BusinessException("RG-9.1",
                    "Ce compte est deja rattache au chauffeur " + existing.getFullName(),
                    HttpStatus.CONFLICT);
        });
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private Driver find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", id));
    }
}
