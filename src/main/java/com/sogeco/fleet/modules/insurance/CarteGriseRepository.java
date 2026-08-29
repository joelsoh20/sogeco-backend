package com.sogeco.fleet.modules.insurance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CarteGriseRepository extends JpaRepository<CarteGrise, Long> {

    @EntityGraph(attributePaths = {"vehicle"})
    Page<CarteGrise> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteGrise> findByVehicleIdOrderByExpiryDateDesc(Long vehicleId);

    /** Ce que CE chauffeur a lui-meme saisi — jamais les entrees d'un autre. */
    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteGrise> findByCreatedByUserIdOrderByExpiryDateDesc(Long userId);

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteGrise> findByExpiryDateLessThanEqual(LocalDate limit);
}
