package com.sogeco.fleet.modules.insurance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CarteRoseRepository extends JpaRepository<CarteRose, Long> {

    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    Page<CarteRose> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByVehicleIdOrderByValidToDesc(Long vehicleId);

    /** Ce que CE chauffeur a lui-meme saisi — jamais les entrees d'un autre. */
    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByCreatedByUserIdOrderByValidToDesc(Long userId);

    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByValidToLessThanEqual(LocalDate limit);
}
