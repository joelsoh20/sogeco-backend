package com.sogeco.fleet.modules.route;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.route.dto.RouteObservedResponse;
import com.sogeco.fleet.modules.route.dto.RouteRequest;
import com.sogeco.fleet.modules.route.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    /** Au-dela de cet ecart, la saisie est signalee comme suspecte. */
    private static final BigDecimal DEVIATION_WARNING_PERCENT = BigDecimal.valueOf(20);

    private final RouteRepository repository;
    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<RouteResponse> list() {
        return repository.findByActiveTrueOrderByLabelAsc().stream()
                .map(RouteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public RouteResponse get(Long id) {
        return RouteResponse.from(find(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public RouteResponse create(RouteRequest request) {
        if (request.originCityId().equals(request.destinationCityId())) {
            throw new BusinessException("RG-13.6",
                    "Un corridor relie deux villes distinctes", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (repository.existsByOriginCityIdAndDestinationCityId(
                request.originCityId(), request.destinationCityId())) {
            throw new DuplicateResourceException("Corridor", "trajet",
                    request.originCityId() + " → " + request.destinationCityId());
        }

        City origin = findCity(request.originCityId());
        City destination = findCity(request.destinationCityId());

        Route route = Route.builder()
                .originCity(origin)
                .destinationCity(destination)
                .label("%s → %s".formatted(origin.getName(), destination.getName()))
                .referenceDistanceKm(request.referenceDistanceKm())
                .referenceDurationMinutes(request.referenceDurationMinutes())
                .referenceFuelLiters(request.referenceFuelLiters())
                .toleranceKm(request.toleranceKm() == null ? BigDecimal.valueOf(5) : request.toleranceKm())
                .corridorGeojson(request.corridorGeojson())
                .build();

        return RouteResponse.from(repository.save(route));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public RouteResponse update(Long id, RouteRequest request) {
        Route route = find(id);

        route.setReferenceDistanceKm(request.referenceDistanceKm());
        route.setReferenceDurationMinutes(request.referenceDurationMinutes());
        route.setReferenceFuelLiters(request.referenceFuelLiters());
        if (request.toleranceKm() != null) {
            route.setToleranceKm(request.toleranceKm());
        }
        if (request.corridorGeojson() != null) {
            route.setCorridorGeojson(request.corridorGeojson());
        }

        return RouteResponse.from(route);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    /** Corridor correspondant a un couple de villes, s'il existe. */
    @Transactional(readOnly = true)
    public Optional<Route> findByCities(Long originCityId, Long destinationCityId) {
        if (originCityId == null || destinationCityId == null) {
            return Optional.empty();
        }
        return repository.findByOriginCityIdAndDestinationCityId(originCityId, destinationCityId);
    }

    /**
     * Compare la reference saisie aux moyennes constatees.
     *
     * Ces valeurs conditionnent toute la detection d'anomalies : une
     * reference approximative saisie le premier jour fausserait
     * durablement les alertes de surconsommation.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public RouteObservedResponse observed(Long id, long missionCount,
                                          BigDecimal observedDistance, Integer observedDuration) {
        Route route = find(id);
        BigDecimal reference = route.getReferenceDistanceKm();

        BigDecimal deviation = null;
        String warning = null;

        if (reference != null && observedDistance != null && reference.signum() != 0) {
            deviation = observedDistance.subtract(reference)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(reference, 2, RoundingMode.HALF_UP);

            if (deviation.abs().compareTo(DEVIATION_WARNING_PERCENT) > 0) {
                warning = "La reference s'ecarte de %s%% de la moyenne observee sur %d missions"
                        .formatted(deviation, missionCount);
            }
        }

        return new RouteObservedResponse(missionCount, observedDistance, observedDuration,
                reference, deviation, warning);
    }

    private City findCity(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", id));
    }

    Route find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", id));
    }
}
