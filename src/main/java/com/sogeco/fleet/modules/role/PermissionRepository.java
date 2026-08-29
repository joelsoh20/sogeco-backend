package com.sogeco.fleet.modules.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    List<Permission> findByCodeIn(Set<String> codes);

    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
