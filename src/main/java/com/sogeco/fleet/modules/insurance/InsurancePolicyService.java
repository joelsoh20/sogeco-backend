package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.insurance.dto.InsurancePolicyRequest;
import com.sogeco.fleet.modules.insurance.dto.InsurancePolicyResponse;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.partner.PartnerRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contrats d'assurance.
 *
 * Regles couvertes : RG-8.1 a RG-8.6.
 *
 * L'echeance portee ici est verifiee DIRECTEMENT par
 * VehicleService.blockingReasons() au moment de l'affectation — pas
 * via un document generique intermediaire, la table documents exigeant
 * un fichier reel que l'entree d'une police ne fournit pas toujours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsurancePolicyService {

    private static final String ENTITY = "InsurancePolicy";

    private final InsurancePolicyRepository repository;
    private final PartnerRepository partnerRepository;
    private final VehicleRepository vehicleRepository;
    private final AuditService auditService;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public com.sogeco.fleet.common.dto.PageResponse<InsurancePolicyResponse> list(Pageable pageable) {
        return com.sogeco.fleet.common.dto.PageResponse.from(repository.findAllBy(pageable), InsurancePolicyResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public InsurancePolicyResponse get(Long id) {
        return InsurancePolicyResponse.from(repository.findWithVehiclesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", id)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<InsurancePolicyResponse> forVehicle(Long vehicleId) {
        return repository.findActiveForVehicle(vehicleId).map(InsurancePolicyResponse::from)
                .map(List::of).orElse(List.of());
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE')")
    public InsurancePolicyResponse create(InsurancePolicyRequest request) {
        if (repository.existsByPolicyNumber(request.policyNumber())) {
            throw new DuplicateResourceException("Police d'assurance", "numero", request.policyNumber());
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException("RG-8.1",
                    "La date de fin doit etre posterieure a la date de debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        InsurancePolicy policy = InsurancePolicy.builder()
                .policyNumber(request.policyNumber())
                .insurer(findInsurer(request.partnerId()))
                .coverageType(request.coverageType())
                .category(request.category())
                .vehicleRegistration(emptyToNull(request.vehicleRegistration()))
                .premiumAmount(request.premiumAmount())
                .paymentFrequency(request.paymentFrequency() == null
                        ? com.sogeco.fleet.common.enums.PaymentFrequency.ANNUEL : request.paymentFrequency())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(PolicyStatus.ACTIVE)
                .vehicles(resolveVehicles(request.vehicleIds(), request.vehicleRegistration()))
                .notes(request.notes())
                .build();

        InsurancePolicy saved = repository.save(policy);
        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.POLICY_CREATED, ENTITY, saved.getId(), null);
        log.info("Police {} creee par {}, {} camion(s) couvert(s)",
                saved.getPolicyNumber(), SecurityUtils.currentUserEmail(), saved.getVehicles().size());

        return InsurancePolicyResponse.from(saved);
    }

    /**
     * Correction d'une police existante (RG-8-EDIT) -- distinct de renew(),
     * qui cree une nouvelle police et cloture l'ancienne. Le statut n'est
     * jamais touche ici : il reste du ressort de renew()/cancel().
     */
    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public InsurancePolicyResponse update(Long id, InsurancePolicyRequest request) {
        InsurancePolicy policy = repository.findWithVehiclesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", id));

        EditWindowGuard.assertEditable(policy.getCreatedAt(),
                settingService.getInt("policy.edit_window_hours", 24), "RG-8-EDIT", "Cette police");

        if (!request.policyNumber().equals(policy.getPolicyNumber())
                && repository.existsByPolicyNumberAndIdNot(request.policyNumber(), id)) {
            throw new DuplicateResourceException("Police d'assurance", "numero", request.policyNumber());
        }
        if (!request.endDate().isAfter(request.startDate())) {
            throw new BusinessException("RG-8.1",
                    "La date de fin doit etre posterieure a la date de debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        policy.setPolicyNumber(request.policyNumber());
        policy.setInsurer(findInsurer(request.partnerId()));
        policy.setCoverageType(request.coverageType());
        policy.setCategory(request.category());
        policy.setVehicleRegistration(emptyToNull(request.vehicleRegistration()));
        policy.setPremiumAmount(request.premiumAmount());
        policy.setPaymentFrequency(request.paymentFrequency() == null
                ? com.sogeco.fleet.common.enums.PaymentFrequency.ANNUEL : request.paymentFrequency());
        policy.setStartDate(request.startDate());
        policy.setEndDate(request.endDate());
        policy.setVehicles(resolveVehicles(request.vehicleIds(), request.vehicleRegistration()));
        policy.setNotes(request.notes());

        log.info("Police {} corrigee par {}", policy.getPolicyNumber(), SecurityUtils.currentUserEmail());
        return InsurancePolicyResponse.from(policy);
    }

    /**
     * Renouvellement : nouvelle periode, memes camions par defaut sauf
     * precision contraire. L'ancienne police passe a EXPIREE plutot que
     * d'etre modifiee en place — l'historique des primes reste lisible.
     */
    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE')")
    public InsurancePolicyResponse renew(Long id, InsurancePolicyRequest request) {
        InsurancePolicy previous = repository.findWithVehiclesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", id));

        previous.setStatus(PolicyStatus.EXPIREE);

        return create(request);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_MANAGE')")
    public void cancel(Long id) {
        InsurancePolicy policy = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", id));
        policy.setStatus(PolicyStatus.RESILIEE);
    }

    /** Polices arrivant a echeance, pour la tache planifiee d'alerte. */
    @Transactional(readOnly = true)
    public List<InsurancePolicy> findExpiringBefore(LocalDate limit) {
        return repository.findByStatusAndEndDateLessThanEqual(PolicyStatus.ACTIVE, limit);
    }

    /**
     * Lie la police au(x) camion(s) du parc — condition necessaire au blocage
     * d'affectation RG-4.5 si l'assurance expire. Deux sources, la premiere
     * ayant priorite : vehicleIds (ancien flux, garde pour renew()) sinon une
     * correspondance par immatriculation sur le "Genre" saisi. Aucune des deux
     * n'etant obligatoire desormais : une police pour un camion pas encore
     * enregistre reste creable, simplement sans effet bloquant pour l'instant.
     */
    private Set<Vehicle> resolveVehicles(Set<Long> ids, String vehicleRegistration) {
        if (ids != null && !ids.isEmpty()) {
            Set<Vehicle> vehicles = new HashSet<>(vehicleRepository.findAllById(ids));
            if (vehicles.size() != ids.size()) {
                throw new BusinessException("RG-8.1",
                        "Un ou plusieurs camions sont introuvables", HttpStatus.UNPROCESSABLE_CONTENT);
            }
            return vehicles;
        }
        if (vehicleRegistration != null && !vehicleRegistration.isBlank()) {
            return vehicleRepository.findByRegistrationNumberIgnoreCase(vehicleRegistration.trim())
                    .map(v -> new HashSet<>(Set.of(v)))
                    .orElseGet(HashSet::new);
        }
        return new HashSet<>();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private Partner findInsurer(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assureur", id));
    }
}
