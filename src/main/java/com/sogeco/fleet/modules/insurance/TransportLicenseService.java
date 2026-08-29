package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.insurance.dto.TransportLicenseRequest;
import com.sogeco.fleet.modules.insurance.dto.TransportLicenseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** Licence de transport — un seul document pour toute la flotte, jamais par camion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportLicenseService {

    private final TransportLicenseRepository repository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<TransportLicenseResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), TransportLicenseResponse::from);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE')")
    public TransportLicenseResponse create(TransportLicenseRequest request) {
        if (repository.existsByReference(request.reference())) {
            throw new DuplicateResourceException("Licence de transport", "reference", request.reference());
        }

        TransportLicense license = TransportLicense.builder()
                .reference(request.reference())
                .issuingAuthority(request.issuingAuthority())
                .receiptNumber(request.receiptNumber())
                .power(request.power())
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .cost(request.cost())
                .notes(request.notes())
                .build();

        TransportLicense saved = repository.save(license);

        log.info("Licence de transport {} enregistree pour la flotte par {}",
                request.reference(), SecurityUtils.currentUserEmail());

        return TransportLicenseResponse.from(saved);
    }

    /** Licence(s) arrivant a echeance, pour l'echeancier unifie. */
    @Transactional(readOnly = true)
    public List<TransportLicense> findExpiringBefore(LocalDate limit) {
        return repository.findByStatusAndExpiryDateLessThanEqual(PolicyStatus.ACTIVE, limit);
    }
}
