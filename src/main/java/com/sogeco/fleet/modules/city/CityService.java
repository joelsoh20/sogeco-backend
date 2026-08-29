package com.sogeco.fleet.modules.city;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.util.CoordinateParser;
import com.sogeco.fleet.modules.city.dto.CityRequest;
import com.sogeco.fleet.modules.city.dto.CityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository repository;

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<CityResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable), CityResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<CityResponse> listActive() {
        return repository.findByActiveTrueOrderByNameAsc().stream().map(CityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public CityResponse get(Long id) {
        return CityResponse.from(find(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public CityResponse create(CityRequest request) {
        String code = request.code() == null || request.code().isBlank()
                ? buildCode(request.name())
                : request.code();

        if (repository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Ville", "code", code);
        }

        City city = City.builder()
                .code(code.toUpperCase())
                .name(request.name())
                .region(request.region())
                .hasSite(Boolean.TRUE.equals(request.hasSite()))
                .build();

        applyCoordinates(city, request.coordinates());
        return CityResponse.from(repository.save(city));
    }

    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public CityResponse update(Long id, CityRequest request) {
        City city = find(id);

        // Code absent : on garde celui deja attribue plutot que d'en regenerer un.
        if (request.code() != null && !request.code().isBlank()) {
            repository.findByCodeIgnoreCase(request.code())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Ville", "code", request.code());
                    });
            city.setCode(request.code().toUpperCase());
        }

        city.setName(request.name());
        city.setRegion(request.region());
        if (request.hasSite() != null) {
            city.setHasSite(request.hasSite());
        }
        applyCoordinates(city, request.coordinates());

        return CityResponse.from(city);
    }

    /**
     * Desactivation logique : une ville reste referencee par des missions
     * passees, elle ne doit jamais disparaitre (RG-13.2, principe general).
     */
    @Transactional
    @PreAuthorize("hasAuthority('CITY_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    /**
     * Cree la ville a la volee si elle n'existe pas. Utilise par le
     * geocodage des adresses de livraison : le referentiel est ouvert.
     */
    @Transactional
    public City findOrCreate(String name, String region) {
        return repository.findByNameIgnoreCase(name).orElseGet(() -> {
            log.info("Creation automatique de la ville {}", name);
            return repository.save(City.builder()
                    .code(buildCode(name))
                    .name(name)
                    .region(region)
                    .hasSite(false)
                    .build());
        });
    }

    private void applyCoordinates(City city, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        CoordinateParser.GeoPoint point = CoordinateParser.parse(raw);
        city.setLatitude(point.latitude());
        city.setLongitude(point.longitude());

        if (CoordinateParser.isOutsideCameroon(point.latitude(), point.longitude())) {
            log.warn("Coordonnees hors Cameroun pour la ville {} : {}", city.getName(), raw);
        }
    }

    /** Code court derive du nom, suffixe si deja pris. */
    private String buildCode(String name) {
        String base = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        base = base.length() >= 3 ? base.substring(0, 3) : base;
        String candidate = base;
        int suffix = 1;
        while (repository.existsByCodeIgnoreCase(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private City find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", id));
    }
}
