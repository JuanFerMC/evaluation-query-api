package com.crediticio.evaluationquery.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.proc.BadJWTException;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decodificador del token de servicio S1 (HU14 y proyección, composición verificada en
 * DecodificadorServicioJwtCaracterizacionTest): RS256 con clave elegida por kid (una o dos, para rotar), typ,
 * kid configurado, iss, aud, sub, exp e iat obligatorios, tolerancia de reloj, duración máxima, scope con el
 * permiso S1 permitido (generación o actualización de proyección) y sin claim role. No se registra como bean: la cadena de usuarios usa el JwtDecoder de
 * JwtConfig y nunca acepta tokens de servicio.
 */
public final class ServicioJwtConfig {

    static final String CLAIM_ALCANCE = "scope";

    private ServicioJwtConfig() {
    }

    public static JwtDecoder decodificador(ServicioJwtProperties propiedades) {
        Map<String, RSAPublicKey> claves = new LinkedHashMap<>();
        claves.put(requerirTexto(propiedades.idClave(), "evaluationquery.servicio-jwt.id-clave"),
                leerClavePublica(propiedades.clavePublica()));
        if (propiedades.idClaveAnterior() != null && !propiedades.idClaveAnterior().isBlank()) {
            if (claves.containsKey(propiedades.idClaveAnterior())) {
                throw new IllegalStateException("La clave anterior del token de servicio debe tener otro kid");
            }
            claves.put(propiedades.idClaveAnterior(), leerClavePublica(propiedades.clavePublicaAnterior()));
        }
        return decodificador(claves, propiedades);
    }

    /**
     * El iat obligatorio se verifica en Nimbus: Spring rellena un iat ausente con exp - 1 s antes de aplicar los
     * validadores, que nunca lo verían nulo.
     */
    public static JwtDecoder decodificador(Map<String, RSAPublicKey> clavesPorKid, ServicioJwtProperties propiedades) {
        List<JWK> claves = clavesPorKid.entrySet().stream()
                .map(entrada -> (JWK) new RSAKey.Builder(entrada.getValue())
                        .keyID(entrada.getKey())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .build())
                .toList();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(new JWKSet(claves)))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .jwtProcessorCustomizer(procesador -> procesador.setJWTClaimsSetVerifier((claimsSet, contexto) -> {
                    if (claimsSet.getIssueTime() == null) {
                        throw new BadJWTException("iat ausente");
                    }
                }))
                .build();
        decoder.setJwtValidator(validador(Set.copyOf(clavesPorKid.keySet()), propiedades));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> validador(Set<String> kids, ServicioJwtProperties propiedades) {
        String emisor = requerirTexto(propiedades.emisor(), "evaluationquery.servicio-jwt.emisor");
        String sujeto = requerirTexto(propiedades.sujeto(), "evaluationquery.servicio-jwt.sujeto");
        String audiencia = requerirTexto(propiedades.audiencia(), "evaluationquery.servicio-jwt.audiencia");
        String permiso = requerirTexto(propiedades.permiso(), "evaluationquery.servicio-jwt.permiso");
        String permisoProyeccion = requerirTexto(propiedades.permisoProyeccion(),
                "evaluationquery.servicio-jwt.permiso-proyeccion");
        String tipo = requerirTexto(propiedades.tipo(), "evaluationquery.servicio-jwt.tipo");
        Duration tolerancia = requerirDuracion(propiedades.tolerancia(), "evaluationquery.servicio-jwt.tolerancia");
        Duration duracionMaxima = requerirDuracion(propiedades.duracionMaxima(),
                "evaluationquery.servicio-jwt.duracion-maxima");
        JwtTypeValidator validadorTipo = new JwtTypeValidator(tipo);
        validadorTipo.setAllowEmpty(false);
        return new DelegatingOAuth2TokenValidator<>(
                validadorTipo,
                // Set.copyOf no admite contains(null): sin la comprobación, un token sin kid lanzaría una excepción
                validador("kid", jwt -> jwt.getHeaders().get("kid") instanceof String kid && kids.contains(kid)),
                new JwtIssuerValidator(emisor),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiencias -> audiencias != null && audiencias.contains(audiencia)),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sujeto::equals),
                new JwtTimestampValidator(tolerancia),
                validador("exp", jwt -> jwt.getExpiresAt() != null),
                validador("iat-futuro", jwt -> jwt.getIssuedAt() == null
                        || !jwt.getIssuedAt().isAfter(Instant.now().plus(tolerancia))),
                validador("vida", jwt -> jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plus(duracionMaxima))),
                new JwtClaimValidator<String>(CLAIM_ALCANCE,
                        alcance -> alcance != null && List.of(alcance.split(" ")).stream()
                                .anyMatch(Set.of(permiso, permisoProyeccion)::contains)),
                validador("role", jwt -> !jwt.hasClaim(JwtConfig.CLAIM_ROL)));
    }

    private static OAuth2TokenValidator<Jwt> validador(String nombre, Predicate<Jwt> condicion) {
        return jwt -> condicion.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", nombre, null));
    }

    private static String requerirTexto(String valor, String propiedad) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la propiedad " + propiedad);
        }
        return valor;
    }

    private static Duration requerirDuracion(Duration valor, String propiedad) {
        if (valor == null || valor.isNegative() || valor.isZero()) {
            throw new IllegalStateException("Falta la propiedad " + propiedad);
        }
        return valor;
    }

    private static RSAPublicKey leerClavePublica(Resource recurso) {
        if (recurso == null || !recurso.exists()) {
            throw new IllegalStateException("No se encontró la clave pública del token de servicio configurada");
        }
        try (InputStream entrada = recurso.getInputStream()) {
            return RsaKeyConverters.x509().convert(entrada);
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible leer la clave pública del token de servicio", ex);
        }
    }
}
