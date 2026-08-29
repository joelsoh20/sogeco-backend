package com.sogeco.fleet.modules.agency;

import com.sogeco.fleet.common.enums.SiteType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AgencyRepository extends JpaRepository<Agency, Long>, JpaSpecificationExecutor<Agency> {

    Optional<Agency> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = "city")
    List<Agency> findByActiveTrueOrderByNameAsc();

    List<Agency> findByCityIdAndActiveTrue(Long cityId);

    List<Agency> findBySiteTypeAndActiveTrue(SiteType siteType);

    long countByActiveTrue();
}
