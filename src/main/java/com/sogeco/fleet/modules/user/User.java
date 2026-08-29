package com.sogeco.fleet.modules.user;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.role.Permission;
import com.sogeco.fleet.modules.role.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compte utilisateur.
 *
 * L'authentification se fait par mot de passe ou par compte Google
 * (RG-1.1). Un utilisateur n'est jamais supprime, seulement suspendu.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 150, unique = true)
    private String email;

    /** Nul si le compte n'est rattache qu'a Google. */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "phone", length = 30)
    private String phone;

    /** Perimetre du gestionnaire : filtre les camions, chauffeurs et missions qu'il voit (RG-13.4). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIF;

    @Column(name = "google_id", length = 120, unique = true)
    private String googleId;

    @Column(name = "totp_secret", length = 128)
    private String totpSecret;

    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private Boolean totpEnabled = Boolean.FALSE;

    @Builder.Default
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_reset_token", length = 120)
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Builder.Default
    @Column(name = "must_change_password", nullable = false)
    private Boolean mustChangePassword = Boolean.FALSE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    // ------------------------------------------------------------------
    // Comportement
    // ------------------------------------------------------------------

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIF;
    }

    /** Verrouillage temporaire apres echecs successifs (RG-1.3). */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void registerSuccessfulLogin() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void registerFailedAttempt(int maxAttempts, int lockMinutes) {
        this.failedAttempts = this.failedAttempts == null ? 1 : this.failedAttempts + 1;
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plusSeconds(lockMinutes * 60L);
        }
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDU;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIF;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    /** Codes de permission cumules de tous les roles de l'utilisateur. */
    public Set<String> getPermissionCodes() {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
    }

    public Set<String> getRoleCodes() {
        return roles.stream().map(Role::getCode).collect(Collectors.toSet());
    }

    public boolean hasRole(String roleCode) {
        return roles.stream().anyMatch(role -> role.getCode().equals(roleCode));
    }
}
