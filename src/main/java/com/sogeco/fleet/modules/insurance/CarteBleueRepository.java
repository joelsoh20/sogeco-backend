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

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteBleue> findByVehicleIdOrderByExpiryDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"vehicle"})
    List<CarteBleue> findByExpiryDateLessThanEqual(LocalDate limit);

    boolean existsByReceiptNumber(String receiptNumber);
}
