package com.sogeco.fleet.modules.role;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findWithPermissionsByCode(String code);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findByActiveTrueOrderByLabelAsc();

    boolean existsByCode(String code);
}
