package com.sogeco.fleet.modules.client;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    Optional<ServiceType> findByCode(String code);

    List<ServiceType> findByActiveTrueOrderByLabelAsc();
}
