package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.enums.BonusStatus;
import com.sogeco.fleet.common.enums.RatingCriterion;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.driver.dto.*;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Notation et primes.
 *
 * Le score global est la moyenne ponderee des cinq criteres, les
 * ponderations venant des parametres systeme (RG-9.5, RG-9.7).
 *
 * Quatre criteres seront alimentes automatiquement a partir du sprint 4
 * (consommation) et du sprint 5 (vitesse, ponctualite). RESPECT_REGLES
 * reste saisi par un responsable, faute de source de donnees (RG-9.6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverPerformanceService {

    private static final String ENTITY = "Driver";

    private final DriverRepository driverRepository;
    private final DriverRatingRepository ratingRepository;
    private final DriverBonusRepository bonusRepository;
    private final SettingService settingService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------
    // Notation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<RatingResponse> ratingsOf(Long driverId) {
        return ratingRepository.findTop25ByDriverIdOrderByPeriodMonthDescCriterionAsc(driverId)
                .stream().map(RatingResponse::from).toList();
    }

    /**
     * Evolution du score global, mois par mois — pour le graphique de
     * tendance de l'ecran Chauffeurs. Reprend exactement la meme
     * ponderation que recomputeScore() : un point de cette courbe ne
     * doit jamais raconter un chiffre different de celui qui aurait
     * ete calcule comme score courant a cette date-la.
     *
     * Contrairement au score courant, aucun seuil de missions
     * minimum n'est applique ici : une note deja saisie pour un mois
     * passe reste un point de donnee valable pour l'historique, meme
     * si le nombre de missions du chauffeur a depuis change.
     */
    @Transactional(readOnly = true)
    public List<PerformancePoint> performanceHistory(Long driverId) {
        List<DriverRating> allRatings = ratingRepository.findByDriverIdOrderByPeriodMonthAsc(driverId);

        Map<LocalDate, List<DriverRating>> byMonth = allRatings.stream()
                .collect(Collectors.groupingBy(DriverRating::getPeriodMonth, LinkedHashMap::new, Collectors.toList()));

        List<PerformancePoint> points = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DriverRating>> entry : byMonth.entrySet()) {
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;

            for (DriverRating rating : entry.getValue()) {
                BigDecimal weight = BigDecimal.valueOf(
                        settingService.getInt(rating.getCriterion().getWeightSettingKey(), 20));
                weightedSum = weightedSum.add(rating.getScore100().multiply(weight));
                totalWeight = totalWeight.add(weight);
            }

            if (totalWeight.signum() > 0) {
                points.add(new PerformancePoint(entry.getKey(),
                        weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP)));
            }
        }
        return points;
    }

    /**
     * Saisie ou correction d'une note. Une seule ligne par chauffeur,
     * mois et critere : une nouvelle saisie remplace la precedente.
     */
    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_RATE')")
    public RatingResponse rate(Long driverId, RatingRequest request) {
        Driver driver = findDriver(driverId);
        LocalDate period = normalize(request.periodMonth());

        if (period.isAfter(currentPeriod())) {
            throw new BusinessException("RG-9.5",
                    "Impossible de noter un mois a venir", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        DriverRating rating = ratingRepository
                .findByDriverIdAndPeriodMonthAndCriterion(driverId, period, request.criterion())
                .orElseGet(() -> DriverRating.builder()
                        .driver(driver)
                        .periodMonth(period)
                        .criterion(request.criterion())
                        .build());

        rating.setScore100(request.score100());
        rating.setComment(request.comment());
        rating.setIsAutomatic(false);
        rating.setRatedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null));

        DriverRating saved = ratingRepository.save(rating);
        recomputeScore(driverId, period);

        return RatingResponse.from(saved);
    }

    /**
     * Recalcule le score global du chauffeur pour un mois.
     *
     * Aucun score n'est produit sans au moins une note saisie pour la
     * periode (RG-9.8) : mieux vaut ne pas noter que noter faux. Les
     * criteres absents sont ignores, et les ponderations renormalisees
     * sur les seuls criteres disponibles.
     *
     * Ne conditionne plus ce calcul a un nombre minimal de missions
     * cloturees dans l'appli : un usage quotidien sans mission formelle
     * (tour de ville, cf. DriverService.gpsKilometers()) aurait sinon
     * laisse le score bloque indefiniment, meme avec des notes saisies
     * par un responsable.
     */
    @Transactional
    public Optional<BigDecimal> recomputeScore(Long driverId, LocalDate period) {
        Driver driver = findDriver(driverId);

        List<DriverRating> ratings = ratingRepository.findByDriverIdAndPeriodMonth(driverId, normalize(period));
        if (ratings.isEmpty()) {
            driver.setPerformanceScore(null);
            return Optional.empty();
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (DriverRating rating : ratings) {
            BigDecimal weight = BigDecimal.valueOf(
                    settingService.getInt(rating.getCriterion().getWeightSettingKey(), 20));
            weightedSum = weightedSum.add(rating.getScore100().multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        if (totalWeight.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal score = weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP);
        driver.setPerformanceScore(score);
        return Optional.of(score);
    }

    // ------------------------------------------------------------------
    // Primes
    // ------------------------------------------------------------------

    /**
     * Propose une prime selon le bareme parametre (RG-9.11).
     * Le montant reste ajustable a la validation, avec justification.
     */
    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_BONUS_MANAGE')")
    public BonusResponse propose(Long driverId, LocalDate period) {
        Driver driver = findDriver(driverId);
        LocalDate month = normalize(period);

        if (driver.getPerformanceScore() == null) {
            throw new BusinessException("RG-9.8",
                    "Ce chauffeur n'a pas de score pour cette periode : activite insuffisante ou notes manquantes",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        BigDecimal amount = computeBonusAmount(driver.getPerformanceScore());

        DriverBonus bonus = bonusRepository.findByDriverIdAndPeriodMonth(driverId, month)
                .orElseGet(() -> DriverBonus.builder()
                        .driver(driver)
                        .periodMonth(month)
                        .build());

        if (!bonus.isEditable()) {
            throw new BusinessException("RG-9.11",
                    "Cette prime est deja versee, elle n'est plus modifiable", HttpStatus.CONFLICT);
        }

        bonus.setAmount(amount);
        bonus.setPerformanceScore(driver.getPerformanceScore());
        bonus.setStatus(BonusStatus.PROPOSEE);
        bonus.setReason("Bareme automatique, score %s/100".formatted(driver.getPerformanceScore()));

        return BonusResponse.from(bonusRepository.save(bonus));
    }

    @Transactional
    @PreAuthorize("hasAuthority('DRIVER_BONUS_MANAGE')")
    public BonusResponse decide(Long bonusId, BonusDecisionRequest request) {
        DriverBonus bonus = bonusRepository.findById(bonusId)
                .orElseThrow(() -> new ResourceNotFoundException("Prime", bonusId));

        if (bonus.getStatus() == BonusStatus.VERSEE) {
            throw new BusinessException("RG-9.11",
                    "Cette prime est deja versee", HttpStatus.CONFLICT);
        }

        if (request.amount() != null && request.amount().compareTo(bonus.getAmount()) != 0) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new BusinessException("RG-9.11",
                        "Un ajustement du montant doit etre justifie", HttpStatus.UNPROCESSABLE_CONTENT);
            }
            log.info("Prime de {} ajustee de {} a {} par {} — {}",
                    bonus.getDriver().getFullName(), bonus.getAmount(), request.amount(),
                    SecurityUtils.currentUserEmail(), request.reason());
            bonus.setAmount(request.amount());
            bonus.setReason(request.reason());
        }

        bonus.setStatus(request.status());
        bonus.setGrantedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null));
        bonus.setGrantedAt(Instant.now());
        if (request.status() == BonusStatus.VERSEE) {
            bonus.setPaidAt(Instant.now());
        }

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.BONUS_GRANTED,
                ENTITY, bonus.getDriver().getId(), null, null,
                "\"%s FCFA — %s\"".formatted(bonus.getAmount(), request.status()));

        return BonusResponse.from(bonus);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SALARY_READ')")
    public List<BonusResponse> bonusesOf(Long driverId) {
        return bonusRepository.findByDriverIdOrderByPeriodMonthDesc(driverId)
                .stream().map(BonusResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal currentBonusAmount(Long driverId) {
        return bonusRepository.findByDriverIdAndPeriodMonth(driverId, currentPeriod())
                .map(DriverBonus::getAmount)
                .orElse(null);
    }

    // ------------------------------------------------------------------

    private BigDecimal computeBonusAmount(BigDecimal score) {
        int excellent = settingService.getInt("bonus.score_threshold_excellent", 90);
        int good      = settingService.getInt("bonus.score_threshold_good", 70);

        if (score.intValue() >= excellent) {
            return settingService.getDecimal("bonus.amount_excellent", BigDecimal.valueOf(450_000));
        }
        if (score.intValue() >= good) {
            return settingService.getDecimal("bonus.amount_good", BigDecimal.valueOf(200_000));
        }
        return BigDecimal.ZERO;
    }

    /** Les periodes sont toujours ramenees au premier jour du mois. */
    private LocalDate normalize(LocalDate period) {
        LocalDate base = period == null ? LocalDate.now() : period;
        return base.withDayOfMonth(1);
    }

    private LocalDate currentPeriod() {
        return LocalDate.now().withDayOfMonth(1);
    }

    private Driver findDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", id));
    }

    /** Criteres restant a saisir manuellement pour une periode. */
    @Transactional(readOnly = true)
    public List<RatingCriterion> manualCriteria() {
        return List.of(RatingCriterion.values()).stream()
                .filter(criterion -> !criterion.isAutomatic())
                .toList();
    }
}
