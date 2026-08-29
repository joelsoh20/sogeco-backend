package com.sogeco.fleet.modules.city;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CityRepository extends JpaRepository<City, Long>, JpaSpecificationExecutor<City> {

    Optional<City> findByCodeIgnoreCase(String code);

    Optional<City> findByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCase(String code);

    List<City> findByActiveTrueOrderByNameAsc();

    List<City> findByHasSiteTrueAndActiveTrueOrderByNameAsc();
}
