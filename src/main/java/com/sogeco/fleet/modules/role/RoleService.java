package com.sogeco.fleet.modules.role;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.role.dto.*;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String ENTITY = "Role";

    private final RoleRepository repository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_MANAGE') or hasAuthority('USER_MANAGE')")
    public List<RoleResponse> list() {
        return repository.findByActiveTrueOrderByLabelAsc().stream()
                .map(role -> RoleResponse.from(role, countUsers(role)))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<PermissionResponse> listPermissions() {
        return permissionRepository.findAllByOrderByModuleAscCodeAsc().stream()
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse create(RoleRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Role", "code", request.code());
        }

        Role role = repository.save(Role.builder()
                .code(request.code())
                .label(request.label())
                .description(request.description())
                .isSystem(false)
                .build());

        return RoleResponse.from(role, 0);
    }

    /**
     * Remplace les permissions d'un role.
     *
     * Les roles systeme restent modifiables : SOGECO peut vouloir retirer
     * une permission au Superviseur. Seule leur suppression est interdite.
     */
    @Transactional
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse updatePermissions(Long id, PermissionUpdateRequest request) {
        Role role = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));

        if (Role.ADMIN.equals(role.getCode())) {
            throw new BusinessException("RG-13.3",
                    "Les permissions du role Administrateur ne sont pas modifiables",
                    HttpStatus.CONFLICT);
        }

        List<Permission> permissions = permissionRepository.findByCodeIn(request.permissionCodes());
        if (permissions.size() != request.permissionCodes().size()) {
            Set<String> found = new HashSet<>();
            permissions.forEach(permission -> found.add(permission.getCode()));
            Set<String> unknown = new TreeSet<>(request.permissionCodes());
            unknown.removeAll(found);
            throw new BusinessException("RG-13.3",
                    "Permissions inconnues : " + String.join(", ", unknown),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        String before = String.join(",", new TreeSet<>(
                role.getPermissions().stream().map(Permission::getCode).toList()));

        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.ROLE_PERMISSIONS_UPDATED,
                ENTITY, id, null,
                "\"%s\"".formatted(before),
                "\"%s\"".formatted(String.join(",", new TreeSet<>(request.permissionCodes()))));

        log.info("Permissions du role {} modifiees par {}", role.getCode(), SecurityUtils.currentUserEmail());
        return RoleResponse.from(role, countUsers(role));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public void deactivate(Long id) {
        Role role = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException("RG-13.3",
                    "Un role systeme ne peut pas etre supprime", HttpStatus.CONFLICT);
        }
        if (countUsers(role) > 0) {
            throw new BusinessException("RG-13.3",
                    "Ce role est encore attribue a des utilisateurs", HttpStatus.CONFLICT);
        }

        role.deactivate();
    }

    private long countUsers(Role role) {
        return userRepository.countByRolesContaining(role);
    }
}
