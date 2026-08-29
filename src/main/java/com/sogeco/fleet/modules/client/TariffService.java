package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.client.dto.TariffPreviewResponse;
import com.sogeco.fleet.modules.client.dto.TariffRequest;
import com.sogeco.fleet.modules.client.dto.TariffResponse;
import com.sogeco.fleet.modules.route.Route;
import com.sogeco.fleet.modules.route.RouteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Grille tarifaire et resolution du montant a facturer.
 *
 * Le montant est PROPOSE, jamais impose : le gestionnaire peut le
 * corriger a la cloture avec justification (RG-5.5). L'objectif est
 * d'eviter la saisie libre systematique, pas de rigidifier la
 * facturation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TariffService {

    private final TariffRepository repository;
    private final ClientRepository clientRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final RouteRepository routeRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public List<TariffResponse> list() {
        return repository.findByActiveTrueOrderByValidFromDesc().stream()
                .map(TariffResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CLIENT_READ')")
    public List<TariffResponse> listForClient(Long clientId) {
        return repository.findByClientIdAndActiveTrue(clientId).stream()
                .map(TariffResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('TARIFF_MANAGE')")
    public TariffResponse create(TariffRequest request) {
        Tariff tariff = Tariff.builder()
                .client(request.clientId() == null ? null : clientRepository.findById(request.clientId())
                        .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId())))
                .serviceType(serviceTypeRepository.findById(request.serviceTypeId())
                        .orElseThrow(() -> new ResourceNotFoundException("Type de prestation", request.serviceTypeId())))
                .route(resolveRoute(request.routeId()))
                .pricingMode(request.pricingMode())
                .unitPrice(request.unitPrice())
                .minAmount(request.minAmount())
                .validFrom(request.validFrom())
                .validTo(request.validTo())
                .build();

        if (tariff.getValidTo() != null && tariff.getValidTo().isBefore(tariff.getValidFrom())) {
            throw new BusinessException("RG-5.5",
                    "La fin de validite precede le debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        return TariffResponse.from(repository.save(tariff));
    }

    @Transactional
    @PreAuthorize("hasAuthority('TARIFF_MANAGE')")
    public void deactivate(Long id) {
        repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarif", id))
                .deactivate();
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    /**
     * Retient le tarif le plus specifique parmi les candidats valides.
     *
     * Ordre de priorite : client + corridor, puis client seul, puis
     * corridor seul, puis tarif general. A specificite egale, le tarif
     * le plus recemment entre en vigueur l'emporte.
     */
    @Transactional(readOnly = true)
    public Optional<Tariff> resolve(Long clientId, Long serviceTypeId, Long routeId, LocalDate date) {
        if (serviceTypeId == null) {
            return Optional.empty();
        }
        return repository.findCandidates(clientId, serviceTypeId, routeId,
                        date == null ? LocalDate.now() : date)
                .stream()
                .max(Comparator.comparingInt(Tariff::specificity)
                        .thenComparing(Tariff::getValidFrom));
    }

    /** Montant propose au gestionnaire avant saisie. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public TariffPreviewResponse preview(Long clientId, Long serviceTypeId, Long routeId,
                                         BigDecimal distanceKm, BigDecimal weightKg) {

        Optional<Tariff> found = resolve(clientId, serviceTypeId, routeId, LocalDate.now());
        if (found.isEmpty()) {
            return TariffPreviewResponse.notFound();
        }

        Tariff tariff = found.get();
        BigDecimal amount = tariff.compute(distanceKm, weightKg);

        String explanation = switch (tariff.getPricingMode()) {
            case FORFAIT -> "Forfait %s".formatted(tariff.getUnitPrice());
            case PAR_KM -> "%s x %s km".formatted(tariff.getUnitPrice(),
                    distanceKm == null ? "?" : distanceKm);
            case PAR_TONNE -> "%s x %s tonnes".formatted(tariff.getUnitPrice(),
                    weightKg == null ? "?" : weightKg.divide(BigDecimal.valueOf(1000), 2,
                            java.math.RoundingMode.HALF_UP));
        };

        if (tariff.getMinAmount() != null
                && tariff.getPricingMode().apply(tariff.getUnitPrice(), distanceKm, weightKg)
                        .compareTo(tariff.getMinAmount()) < 0) {
            explanation += " — montant plancher applique";
        }

        return new TariffPreviewResponse(
                tariff.getId(), tariff.getPricingMode(), tariff.getUnitPrice(), amount, explanation, true);
    }

    private Route resolveRoute(Long routeId) {
        if (routeId == null) {
            return null;
        }
        return routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", routeId));
    }

    Tariff findEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarif", id));
    }
}
