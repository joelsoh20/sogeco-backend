package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.enums.RatingCriterion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DriverRatingRepository extends JpaRepository<DriverRating, Long> {

    List<DriverRating> findByDriverIdAndPeriodMonth(Long driverId, LocalDate periodMonth);

    /** Toutes les evaluations d'un chauffeur, toutes periodes confondues — pour l'evolution du score. */
    List<DriverRating> findByDriverIdOrderByPeriodMonthAsc(Long driverId);

    Optional<DriverRating> findByDriverIdAndPeriodMonthAndCriterion(
            Long driverId, LocalDate periodMonth, RatingCriterion criterion);

    /** Les cinq dernieres evaluations, tous criteres confondus (RG-8.3 initial). */
    List<DriverRating> findTop25ByDriverIdOrderByPeriodMonthDescCriterionAsc(Long driverId);

    List<DriverRating> findByPeriodMonth(LocalDate periodMonth);
}
