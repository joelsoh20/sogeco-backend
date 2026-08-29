package com.sogeco.fleet.modules.quartier;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.util.CoordinateParser;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.quartier.dto.QuartierRequest;
import com.sogeco.fleet.modules.quartier.dto.QuartierResponse;
import com.sogeco.fleet.modules.routing.GeocodingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Referentiel des quartiers, gere comme celui des villes (CityService) :
 * ouvert, extensible depuis l'ecran Missions, sans saisie de coordonnees
 * obligatoire — le geocodage automatique (nom + ville + Cameroun) prend
 * le relais quand elles sont omises.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuartierService {

    private final QuartierRepository repository;
    private final CityRepository cityRepository;
    private final GeocodingClient geocodingClient;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<QuartierResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable), QuartierResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<QuartierResponse> listActiveForCity(Long cityId) {
        return repository.findByCityIdAndActiveTrueOrderByNameAsc(cityId)
                .stream().map(QuartierResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public QuartierResponse get(Long id) {
        return QuartierResponse.from(find(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public QuartierResponse create(QuartierRequest request) {
        City city = findCity(request.cityId());

        if (repository.existsByNameIgnoreCaseAndCityId(request.name(), city.getId())) {
            throw new DuplicateResourceException("Quartier", "nom", request.name());
        }

        Quartier quartier = Quartier.builder()
                .name(request.name())
                .city(city)
                .build();

        applyCoordinates(quartier, request.coordinates());
        return QuartierResponse.from(repository.save(quartier));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public QuartierResponse update(Long id, QuartierRequest request) {
        Quartier quartier = find(id);
        City city = findCity(request.cityId());

        repository.findByNameIgnoreCaseAndCityId(request.name(), city.getId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Quartier", "nom", request.name());
                });

        quartier.setName(request.name());
        quartier.setCity(city);
        applyCoordinates(quartier, request.coordinates());

        return QuartierResponse.from(quartier);
    }

    /** Desactivation logique : un quartier reste referme par des missions passees (RG-13.2). */
    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    private void applyCoordinates(Quartier quartier, String raw) {
        if (raw != null && !raw.isBlank()) {
            CoordinateParser.GeoPoint point = CoordinateParser.parse(raw);
            quartier.setLatitude(point.latitude());
            quartier.setLongitude(point.longitude());

            if (CoordinateParser.isOutsideCameroon(point.latitude(), point.longitude())) {
                log.warn("Coordonnees hors Cameroun pour le quartier {} : {}", quartier.getName(), raw);
            }
            return;
        }

        // Pas de coordonnees saisies a la main : geocodage automatique par nom.
        geocodingClient.geocode("%s, %s, Cameroun".formatted(quartier.getName(), quartier.getCity().getName()))
                .ifPresent(point -> {
                    quartier.setLatitude(point.latitude());
                    quartier.setLongitude(point.longitude());
                });
    }

    private City findCity(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", id));
    }

    Quartier find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quartier", id));
    }
}
