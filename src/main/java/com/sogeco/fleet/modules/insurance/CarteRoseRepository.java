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

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur (RG-13.4). */
    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    Page<CarteRose> findAllByVehicle_City_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByVehicleIdOrderByValidToDesc(Long vehicleId);

    /** Ce que CE chauffeur a lui-meme saisi — jamais les entrees d'un autre. */
    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByCreatedByUserIdOrderByValidToDesc(Long userId);

    @EntityGraph(attributePaths = {"vehicle", "insurer"})
    List<CarteRose> findByValidToLessThanEqual(LocalDate limit);

    /** Vrai si ce camion a deja au moins une carte rose, quelle que soit son echeance. */
    boolean existsByVehicleId(Long vehicleId);
}
