package com.sogeco.fleet.modules.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long>, JpaSpecificationExecutor<Client> {

    Optional<Client> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = "city")
    Page<Client> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "city")
    List<Client> findByActiveTrueOrderByCompanyNameAsc();

    List<Client> findByCompanyNameContainingIgnoreCaseAndActiveTrue(String fragment);

    long countByActiveTrue();
}
