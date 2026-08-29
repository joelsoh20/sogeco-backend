package com.sogeco.fleet.modules.partner;

import com.sogeco.fleet.common.enums.PartnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long>, JpaSpecificationExecutor<Partner> {

    Optional<Partner> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "city")
    Page<Partner> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "city")
    List<Partner> findByPartnerTypeAndActiveTrueOrderByNameAsc(PartnerType partnerType);

    @EntityGraph(attributePaths = "city")
    List<Partner> findByActiveTrueOrderByNameAsc();

    long countByPartnerTypeAndActiveTrue(PartnerType partnerType);
}
