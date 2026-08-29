package com.sogeco.fleet.modules.role;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Role applicatif.
 *
 * Les quatre roles systeme (is_system = true) sont livres avec
 * l'application et ne sont pas supprimables (RG-13.3). L'administrateur
 * peut en creer d'autres et leur affecter des permissions.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends SoftDeletableEntity {

    public static final String ADMIN        = "ROLE_ADMIN";
    public static final String GESTIONNAIRE = "ROLE_GESTIONNAIRE";
    public static final String COMPTABLE    = "ROLE_COMPTABLE";
    public static final String CHAUFFEUR    = "ROLE_CHAUFFEUR";

    @Column(name = "code", nullable = false, length = 40, unique = true)
    private String code;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "description", length = 255)
    private String description;

    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = Boolean.FALSE;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    public void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public void removePermission(Permission permission) {
        this.permissions.remove(permission);
    }
}
