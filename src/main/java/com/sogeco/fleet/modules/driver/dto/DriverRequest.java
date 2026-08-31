package com.sogeco.fleet.modules.driver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Fiche chauffeur")
public record DriverRequest(

        @NotBlank(message = "le matricule est obligatoire")
        @Size(max = 30)
        @Schema(example = "CH-001")
        String matricule,

        @NotBlank(message = "le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Size(max = 30)
        String phone,

        @Past(message = "la date de naissance doit etre dans le passe")
        LocalDate birthDate,

        @NotNull(message = "la date d'embauche est obligatoire")
        LocalDate hireDate,

        @Size(max = 80)
        @Schema(example = "Chauffeur poids lourd")
        String jobTitle,

        @Size(max = 50)
        String licenseNumber,

        @Size(max = 20)
        @Schema(example = "C, E")
        String licenseCategory,

        LocalDate licenseExpiryDate,

        @PositiveOrZero(message = "le salaire ne peut pas etre negatif")
        BigDecimal monthlySalary,

        Long cityId,

        @Schema(description = "Compte applicatif existant a rattacher, facultatif")
        Long userId,

        @Email(message = "l'adresse email n'est pas valide")
        @Size(max = 150)
        @Schema(description = "Present (meme vide) pour creer un compte de connexion (role Chauffeur) : "
                + "email fourni, ou vide pour une connexion par nom et prenom. Absent pour ne pas creer de compte. "
                + "Ignore si userId est fourni.")
        String accountEmail,

        @Size(min = 5, message = "le mot de passe doit comporter au moins 5 caracteres")
        @Schema(description = "Mot de passe initial du compte cree. Si absent, un mot de passe aleatoire est genere. "
                + "Sans effet si userId est fourni (compte deja existant) ou si accountEmail est absent.")
        String password
) {
}
