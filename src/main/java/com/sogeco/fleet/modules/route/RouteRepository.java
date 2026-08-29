package com.sogeco.fleet.modules.route;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    @EntityGraph(attributePaths = {"originCity", "destinationCity"})
    Optional<Route> findByOriginCityIdAndDestinationCityId(Long originCityId, Long destinationCityId);

    @EntityGraph(attributePaths = {"originCity", "destinationCity"})
    List<Route> findByActiveTrueOrderByLabelAsc();

    boolean existsByOriginCityIdAndDestinationCityId(Long originCityId, Long destinationCityId);
}
