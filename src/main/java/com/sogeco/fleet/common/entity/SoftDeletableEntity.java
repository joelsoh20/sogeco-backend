package com.sogeco.fleet.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Socle des entites historisees, jamais supprimees physiquement
 * (camions, chauffeurs, missions, clients...). Voir RG-4.7 et RG-13.2.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public void deactivate() {
        this.active = Boolean.FALSE;
        this.deletedAt = Instant.now();
    }

    public void reactivate() {
        this.active = Boolean.TRUE;
        this.deletedAt = null;
    }
}
