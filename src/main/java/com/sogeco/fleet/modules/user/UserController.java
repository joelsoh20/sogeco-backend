package com.sogeco.fleet.modules.user;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.modules.user.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Utilisateurs", description = "Comptes applicatifs")
public class UserController {

    private final UserService service;

    @GetMapping
    @Operation(summary = "Lister les utilisateurs")
    public PageResponse<UserResponse> list(
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un utilisateur")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer un utilisateur",
            description = "Si aucun mot de passe n'est fourni, un mot de passe temporaire est "
                    + "genere et renvoye UNE SEULE FOIS dans temporaryPassword. Il n'est pas "
                    + "recuperable ensuite : le noter immediatement ou utiliser reset-password.")
    public ResponseEntity<UserCreationResult> create(@Valid @RequestBody UserRequest request) {
        UserCreationResult created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.user().id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Suspendre ou reactiver un compte")
    public UserResponse changeStatus(@PathVariable Long id, @RequestParam UserStatus status) {
        return service.changeStatus(id, status);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reinitialiser le mot de passe et renvoyer une valeur temporaire")
    public PasswordResetResponse resetPassword(@PathVariable Long id) {
        return service.resetPassword(id);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Definir le mot de passe d'un utilisateur (choisi par l'administrateur, pas genere)")
    public ResponseEntity<Void> setPassword(@PathVariable Long id, @Valid @RequestBody SetUserPasswordRequest request) {
        service.setPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Deverrouiller un compte apres echecs successifs")
    public ResponseEntity<Void> unlock(@PathVariable Long id) {
        service.unlock(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un compte (jamais physique — RG-13.2)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
