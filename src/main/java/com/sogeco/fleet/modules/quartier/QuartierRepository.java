package com.sogeco.fleet.modules.quartier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface QuartierRepository extends JpaRepository<Quartier, Long>, JpaSpecificationExecutor<Quartier> {

    List<Quartier> findByCityIdAndActiveTrueOrderByNameAsc(Long cityId);

    Optional<Quartier> findByNameIgnoreCaseAndCityId(String name, Long cityId);

    boolean existsByNameIgnoreCaseAndCityId(String name, Long cityId);
}
