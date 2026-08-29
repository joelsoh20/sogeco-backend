package com.sogeco.fleet.modules.user;

import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.modules.role.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * Charge l'utilisateur avec ses roles et permissions en une requete.
     * Utilise a chaque authentification : sans EntityGraph, on declenche
     * une cascade de requetes N+1 sur chaque connexion.
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "city"})
    Optional<User> findWithRolesByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "city"})
    Optional<User> findWithRolesById(Long id);

    /**
     * Connexion par nom + prenom (alternative a l'email). Pas de contrainte
     * d'unicite sur ce couple en base : peut renvoyer 0, 1 ou plusieurs
     * utilisateurs, a AuthService de decider (une ambiguite est traitee
     * comme un echec, jamais un choix implicite du premier resultat).
     */
    @EntityGraph(attributePaths = {"roles", "roles.permissions", "city"})
    List<User> findWithRolesByFirstNameIgnoreCaseAndLastNameIgnoreCase(String firstName, String lastName);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByEmailIgnoreCase(String email);

    long countByStatus(UserStatus status);

    long countByRolesContaining(Role role);

    List<User> findByRolesContaining(Role role);

    @EntityGraph(attributePaths = {"roles", "city"})
    Page<User> findAllBy(Pageable pageable);
}
