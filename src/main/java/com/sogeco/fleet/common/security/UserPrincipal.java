package com.sogeco.fleet.common.security;

import com.sogeco.fleet.modules.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Utilisateur authentifie place dans le contexte de securite.
 *
 * Les autorites cumulent deux natures :
 *   - les codes de role       (ROLE_ADMIN, ROLE_GESTIONNAIRE...)
 *   - les codes de permission (VEHICLE_CREATE, FINANCE_READ...)
 *
 * hasRole() s'appuie sur les premiers, hasAuthority() sur les seconds.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final String fullName;
    private final Long cityId;
    private final boolean enabled;
    private final boolean totpEnabled;
    private final boolean mustChangePassword;
    private final Set<String> roleCodes;
    private final Set<String> permissionCodes;
    private final Collection<GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.cityId = user.getCity() == null ? null : user.getCity().getId();
        this.enabled = user.isActive() && !user.isLocked();
        this.totpEnabled = Boolean.TRUE.equals(user.getTotpEnabled());
        this.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
        this.roleCodes = user.getRoleCodes();
        this.permissionCodes = user.getPermissionCodes();
        this.authorities = buildAuthorities(this.roleCodes, this.permissionCodes);
    }

    /** Reconstruit depuis les claims du jeton, sans acces base. */
    public UserPrincipal(Long id, String email, String fullName, Long cityId,
                         Set<String> roleCodes, Set<String> permissionCodes) {
        this.id = id;
        this.email = email;
        this.password = null;
        this.fullName = fullName;
        this.cityId = cityId;
        this.enabled = true;
        this.totpEnabled = false;
        this.mustChangePassword = false;
        this.roleCodes = roleCodes;
        this.permissionCodes = permissionCodes;
        this.authorities = buildAuthorities(roleCodes, permissionCodes);
    }

    private static Collection<GrantedAuthority> buildAuthorities(Set<String> roles, Set<String> permissions) {
        List<GrantedAuthority> granted = new ArrayList<>();
        roles.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        permissions.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        return List.copyOf(granted);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAdmin() {
        return roleCodes.contains("ROLE_ADMIN");
    }

    public boolean hasPermission(String code) {
        return permissionCodes.contains(code);
    }
}
