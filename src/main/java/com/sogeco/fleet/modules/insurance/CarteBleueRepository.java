package com.sogeco.fleet.modules.insurance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CarteBleueRepository extends JpaRepository<CarteBleue, Long> {

    @EntityGraph(attributePaths = {"vehicle"})
    Page<CarteBleue> findAllBy(Pageable pageable);

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur (RG-13.4). */
    @EntityGraph(attributePaths = {"vehicle"})
    Page<CarteBleue> findAllByVehicle_City_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteBleue> findByVehicleIdOrderByExpiryDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteBleue> findByExpiryDateLessThanEqual(LocalDate limit);

    boolean existsByReceiptNumber(String receiptNumber);

    boolean existsByReceiptNumberAndIdNot(String receiptNumber, Long id);

    /** Vrai si ce camion a deja au moins une carte bleue, quelle que soit son echeance. */
    boolean existsByVehicleId(Long vehicleId);
}
