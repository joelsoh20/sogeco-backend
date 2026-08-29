package com.sogeco.fleet.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deux identifiants possibles pour se connecter : l'email, ou le
 * couple nom + prenom. L'un des deux doit etre fourni (isIdentifierValid).
 * Prenom et nom ne sont pas uniques en base — AuthService.resolveUser()
 * traite plusieurs correspondances comme un echec, au meme titre
 * qu'aucune, pour ne jamais reveler quels comptes existent.
 */
@Schema(description = "Demande de connexion — par email, ou par nom et prenom")
public record LoginRequest(

        @Email(message = "format d'adresse invalide")
        @Schema(example = "admin@sogeco.cm")
        String email,

        @Size(max = 80)
        @Schema(example = "Jean")
        String firstName,

        @Size(max = 80)
        @Schema(example = "Mbarga")
        String lastName,

        @NotBlank(message = "le mot de passe est obligatoire")
        String password,

        @Schema(description = "Code a 6 chiffres, si la double authentification est active")
        String totpCode
) {
    @AssertTrue(message = "l'email, ou le nom et le prenom, sont obligatoires")
    public boolean isIdentifierValid() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasName = firstName != null && !firstName.isBlank()
                && lastName != null && !lastName.isBlank();
        return hasEmail || hasName;
    }
}
