package com.sogeco.fleet.common.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Double authentification TOTP (RFC 6238), implementee sans dependance.
 *
 * L'algorithme tient en quelques lignes et evite d'introduire une
 * bibliotheque supplementaire : HMAC-SHA1 sur le numero de periode de
 * 30 secondes, puis troncature dynamique sur 6 chiffres.
 *
 * Compatible avec Google Authenticator, Microsoft Authenticator et Authy.
 */
@Service
public class TotpService {

    private static final int    DIGITS       = 6;
    private static final int    PERIOD_SEC   = 30;
    private static final int    SECRET_BYTES = 20;   // 160 bits, recommandation RFC 4226
    private static final int    TOLERANCE    = 1;    // accepte la periode precedente et suivante
    private static final String ALGORITHM    = "HmacSHA1";
    private static final String BASE32       = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Secret partage, a presenter a l'utilisateur en Base32. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * URI otpauth a encoder en QR code cote frontend.
     * Exemple : otpauth://totp/SOGECO:jean@sogeco.cm?secret=...&issuer=SOGECO
     */
    public String buildOtpAuthUri(String secret, String email, String issuer) {
        String label = URLEncoder.encode(issuer + ":" + email, StandardCharsets.UTF_8);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d".formatted(
                label, secret, URLEncoder.encode(issuer, StandardCharsets.UTF_8), DIGITS, PERIOD_SEC);
    }

    /**
     * Verifie un code saisi. La tolerance d'une periode de part et d'autre
     * absorbe les decalages d'horloge entre le telephone et le serveur.
     */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || code.length() != DIGITS) {
            return false;
        }
        int submitted;
        try {
            submitted = Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long currentPeriod = Instant.now().getEpochSecond() / PERIOD_SEC;
        byte[] key = base32Decode(secret);

        for (int offset = -TOLERANCE; offset <= TOLERANCE; offset++) {
            if (generateCode(key, currentPeriod + offset) == submitted) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Algorithme
    // ------------------------------------------------------------------

    private int generateCode(byte[] key, long period) {
        byte[] data = new byte[8];
        long value = period;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }

        byte[] hash;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            hash = mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Calcul TOTP impossible", e);
        }

        // Troncature dynamique (RFC 4226, section 5.4)
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   |  (hash[offset + 3] & 0xFF);

        return binary % (int) Math.pow(10, DIGITS);
    }

    // ------------------------------------------------------------------
    // Base32 (RFC 4648), format attendu par les applications d'authentification
    // ------------------------------------------------------------------

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return result.toString();
    }

    private byte[] base32Decode(String encoded) {
        String clean = encoded.trim().replace("=", "").toUpperCase();
        byte[] result = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int position = BASE32.indexOf(c);
            if (position < 0) {
                throw new IllegalArgumentException("Secret TOTP invalide");
            }
            buffer = (buffer << 5) | position;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
