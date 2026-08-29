package com.sogeco.fleet.modules.role;

import com.sogeco.fleet.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Droit elementaire, verifie par @PreAuthorize("hasAuthority('...')").
 * Referentiel fige, alimente par migration : une permission correspond
 * a une action du code, elle n'est pas creable par l'utilisateur.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, length = 60, unique = true)
    private String code;

    @Column(name = "module", nullable = false, length = 40)
    private String module;

    @Column(name = "label", nullable = false, length = 150)
    private String label;
}
