package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TransportLicenseRepository extends JpaRepository<TransportLicense, Long> {

    Page<TransportLicense> findAllBy(Pageable pageable);

    List<TransportLicense> findByStatusAndExpiryDateLessThanEqual(PolicyStatus status, LocalDate limit);

    long countByStatus(PolicyStatus status);

    boolean existsByReference(String reference);

    boolean existsByReferenceAndIdNot(String reference, Long id);
}
