package com.crediticio.evaluationquery.config;

import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Caracterización de Spring Security 7.1.1 y Nimbus 10.9.1 para la identidad de servicio propuesta (S1,
 * HU14): decodificador con una o dos claves públicas elegidas por kid, y validación independiente de
 * typ, kid, iss, aud, sub, exp, iat, duración, scope y ausencia de role. No usa código de producción:
 * el decodificador se construye aquí con la misma composición prevista. Se conserva como prueba de
 * regresión ante cambios de comportamiento de las librerías (p. ej., Nimbus con un token sin kid).
 */
class DecodificadorServicioJwtCaracterizacionTest {

    private static final String EMISOR = "scoring-api";
    private static final String SUJETO = "scoring-api";
    private static final String AUDIENCIA = "evaluation-query-api-internal";
    private static final String PERMISO = "recomendaciones:generar";
    private static final String PERMISO_PROYECCION = "proyeccion:actualizar";
    private static final JOSEObjectType TIPO = new JOSEObjectType("scoring-svc+jwt");
    private static final String KID_A = "kid-a";
    private static final String KID_B = "kid-b";

    private static final KeyPair CLAVES_A = ClavesRsaPrueba.generar();
    private static final KeyPair CLAVES_B = ClavesRsaPrueba.generar();
    private static final KeyPair CLAVES_NO_CONFIGURADAS = ClavesRsaPrueba.generar();

    private static final JwtDecoder DECODER = decodificador(claves(KID_A, CLAVES_A, KID_B, CLAVES_B), true);

    @Test
    void debeAceptarCadaClaveConfiguradaSegunSuKid() {
        assertThat(DECODER.decode(firmar(CLAVES_A, KID_A, validos().build())).getSubject()).isEqualTo(SUJETO);
        assertThat(DECODER.decode(firmar(CLAVES_B, KID_B, validos().build())).getSubject()).isEqualTo(SUJETO);
    }

    /** Comportamiento de la librería: sin kid, Nimbus no filtra por identificador y prueba todas las claves. */
    @Test
    void sinValidadorDeKidNimbusAceptaUnTokenSinKidFirmadoConUnaClaveConfigurada() {
        JwtDecoder sinValidadorDeKid = decodificador(claves(KID_A, CLAVES_A, KID_B, CLAVES_B), false);

        assertThat(sinValidadorDeKid.decode(firmar(CLAVES_B, null, validos().build())).getSubject())
                .isEqualTo(SUJETO);
    }

    @Test
    void debeRechazarUnTokenSinKid() {
        assertThat(errores(firmar(CLAVES_A, null, validos().build()))).containsExactly("kid");
    }

    @Test
    void debeRechazarUnKidNoConfigurado() {
        assertRechazoDeFirma(firmar(CLAVES_A, "kid-x", validos().build()));
    }

    @Test
    void debeRechazarUnKidFirmadoConOtraClave() {
        assertRechazoDeFirma(firmar(CLAVES_B, KID_A, validos().build()));
        assertRechazoDeFirma(firmar(CLAVES_NO_CONFIGURADAS, KID_A, validos().build()));
    }

    @Test
    void debeRechazarAlgoritmosDistintosDeRs256() throws JOSEException {
        byte[] secreto = new byte[32];
        new SecureRandom().nextBytes(secreto);
        SignedJWT hs256 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KID_A).type(TIPO).build(),
                validos().build());
        hs256.sign(new MACSigner(secreto));

        assertRechazoDeFirma(hs256.serialize());
        assertRechazoDeFirma(new PlainJWT(validos().build()).serialize());
        assertRechazoDeFirma(firmar(CLAVES_A, JWSAlgorithm.RS512, KID_A, TIPO, validos().build()));
    }

    @Test
    void debeExigirElTipoDeServicio() {
        assertThat(errores(firmar(CLAVES_A, JWSAlgorithm.RS256, KID_A, null, validos().build())))
                .containsExactly("typ");
        assertThat(errores(firmar(CLAVES_A, JWSAlgorithm.RS256, KID_A, JOSEObjectType.JWT, validos().build())))
                .containsExactly("typ");
    }

    /** Comportamiento de la librería: JwtTypeValidator compara sin distinguir mayúsculas. */
    @Test
    void elTipoSeComparaSinDistinguirMayusculas() {
        String token = firmar(CLAVES_A, JWSAlgorithm.RS256, KID_A, new JOSEObjectType("SCORING-SVC+JWT"),
                validos().build());

        assertThat(DECODER.decode(token).getSubject()).isEqualTo(SUJETO);
    }

    @Test
    void debeValidarElEmisorDeFormaIndependiente() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().issuer("auth-api").build()))).containsExactly("iss");
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().issuer(null).build()))).containsExactly("iss");
    }

    @Test
    void debeValidarLaAudienciaDeFormaIndependiente() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().audience("evaluation-query-api").build())))
                .containsExactly("aud");
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().audience((String) null).build())))
                .containsExactly("aud");
    }

    @Test
    void debeValidarElSujetoDeFormaIndependiente() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().subject(UUID.randomUUID().toString()).build())))
                .containsExactly("sub");
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().subject(null).build()))).containsExactly("sub");
    }

    @Test
    void debeExigirUnPermisoDeServicioReconocido() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().claim("scope", null).build())))
                .containsExactly("scope");
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().claim("scope", "otro").build())))
                .containsExactly("scope");
        assertThat(DECODER.decode(firmar(CLAVES_A, KID_A, validos().claim("scope", "otro " + PERMISO).build()))
                .getSubject()).isEqualTo(SUJETO);
        assertThat(DECODER.decode(firmar(CLAVES_A, KID_A,
                validos().claim("scope", PERMISO_PROYECCION).build())).getSubject()).isEqualTo(SUJETO);
    }

    @Test
    void debeRechazarUnTokenConRol() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().claim("role", JwtConfig.ROL_ANALISTA).build())))
                .containsExactly("role");
    }

    @Test
    void debeExigirExp() {
        assertThat(errores(firmar(CLAVES_A, KID_A, validos().expirationTime(null).build()))).containsExactly("exp");
    }

    /** Comportamiento de la librería: Spring rellena un iat ausente con exp - 1 s antes de los validadores. */
    @Test
    void sinVerificacionEnNimbusSpringRellenaUnIatAusente() {
        JwtDecoder sinVerificadorDeIat = decodificador(claves(KID_A, CLAVES_A, null, null), true, false);
        // exp dentro de la tolerancia, para que el iat rellenado (exp - 1 s) no caiga en el futuro
        JWTClaimsSet sinIat = validos().issueTime(null)
                .expirationTime(Date.from(Instant.now().plusSeconds(20))).build();

        Jwt jwt = sinVerificadorDeIat.decode(firmar(CLAVES_A, KID_A, sinIat));

        assertThat(jwt.getIssuedAt()).isEqualTo(jwt.getExpiresAt().minusSeconds(1));
    }

    @Test
    void debeExigirIatAntesDeLaConversionDeSpring() {
        assertThatThrownBy(() -> DECODER.decode(firmar(CLAVES_A, KID_A, validos().issueTime(null).build())))
                .isInstanceOf(BadJwtException.class)
                .isNotInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iat ausente");
    }

    @Test
    void debeRechazarUnaVidaMayorDeSesentaSegundos() {
        Instant ahora = Instant.now();
        JWTClaimsSet claims = validos().issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(61))).build();

        assertThat(errores(firmar(CLAVES_A, KID_A, claims))).containsExactly("vida");
    }

    @Test
    void debeAplicarTreintaSegundosDeToleranciaDeReloj() {
        Instant ahora = Instant.now();
        JWTClaimsSet caducadoHace31 = validos().issueTime(Date.from(ahora.minusSeconds(90)))
                .expirationTime(Date.from(ahora.minusSeconds(31))).build();
        JWTClaimsSet caducadoHace20 = validos().issueTime(Date.from(ahora.minusSeconds(80)))
                .expirationTime(Date.from(ahora.minusSeconds(20))).build();

        assertThat(errores(firmar(CLAVES_A, KID_A, caducadoHace31))).singleElement()
                .satisfies(error -> assertThat(error).containsIgnoringCase("expired"));
        assertThat(DECODER.decode(firmar(CLAVES_A, KID_A, caducadoHace20)).getSubject()).isEqualTo(SUJETO);
    }

    @Test
    void debeRechazarUnIatEnElFuturo() {
        Instant ahora = Instant.now();
        JWTClaimsSet claims = validos().issueTime(Date.from(ahora.plusSeconds(120)))
                .expirationTime(Date.from(ahora.plusSeconds(150))).build();

        assertThat(errores(firmar(CLAVES_A, KID_A, claims))).containsExactly("iat-futuro");
    }

    @Test
    void debePermitirLaRotacionConDosClaves() {
        JwtDecoder soloA = decodificador(claves(KID_A, CLAVES_A, null, null), true);
        JwtDecoder soloB = decodificador(claves(KID_B, CLAVES_B, null, null), true);
        String tokenA = firmar(CLAVES_A, KID_A, validos().build());
        String tokenB = firmar(CLAVES_B, KID_B, validos().build());

        assertThatThrownBy(() -> soloA.decode(tokenB)).isInstanceOf(BadJwtException.class);
        assertThat(DECODER.decode(tokenA).getSubject()).isEqualTo(SUJETO);
        assertThat(DECODER.decode(tokenB).getSubject()).isEqualTo(SUJETO);
        assertThatThrownBy(() -> soloB.decode(tokenA)).isInstanceOf(BadJwtException.class);
    }

    @Test
    void unTokenDeUsuarioNoDebeAceptarseComoTokenDeServicio() {
        KeyPair clavesAuth = ClavesRsaPrueba.generar();
        String tokenUsuario = TokensPrueba.firmar(clavesAuth,
                TokensPrueba.claimsValidos("auth-api", JwtConfig.ROL_ADMINISTRADOR).build());

        assertRechazoDeFirma(tokenUsuario);
    }

    @Test
    void unTokenDeServicioNoDebeAceptarseEnElDecodificadorDeUsuarios() {
        JwtDecoder usuarios = JwtConfig.decodificador((RSAPublicKey) ClavesRsaPrueba.generar().getPublic(),
                "auth-api", "evaluation-query-api");

        assertThatThrownBy(() -> usuarios.decode(firmar(CLAVES_A, KID_A, validos().build())))
                .isInstanceOf(BadJwtException.class)
                .isNotInstanceOf(JwtValidationException.class);
    }

    /** Aunque la clave de servicio se instalara por error en la cadena de usuarios, los claims lo rechazan. */
    @Test
    void laSeparacionNoDependeSoloDeLaFirma() {
        JwtDecoder usuariosConClaveDeServicio = JwtConfig.decodificador((RSAPublicKey) CLAVES_A.getPublic(),
                "auth-api", "evaluation-query-api");

        assertThatThrownBy(() -> usuariosConClaveDeServicio.decode(firmar(CLAVES_A, KID_A, validos().build())))
                .isInstanceOfSatisfying(JwtValidationException.class, ex -> assertThat(String.join(" | ",
                        ex.getErrors().stream().map(OAuth2Error::getDescription).toList()))
                        .contains("typ", "iss", "aud", "sub", "role"));
    }

    // ---- composición prevista del decodificador de servicio ----

    private static NimbusJwtDecoder decodificador(Map<String, RSAPublicKey> clavesPorKid, boolean validarKid) {
        return decodificador(clavesPorKid, validarKid, true);
    }

    /**
     * El iat obligatorio se verifica en Nimbus (jwtProcessorCustomizer, aplicado después del verificador
     * vacío que instala Spring): MappedJwtClaimSetConverter rellena un iat ausente con exp - 1 s, por lo
     * que un validador de Spring nunca lo vería nulo.
     */
    private static NimbusJwtDecoder decodificador(Map<String, RSAPublicKey> clavesPorKid, boolean validarKid,
            boolean exigirIat) {
        List<JWK> claves = clavesPorKid.entrySet().stream()
                .map(entrada -> (JWK) new RSAKey.Builder(entrada.getValue())
                        .keyID(entrada.getKey())
                        .keyUse(KeyUse.SIGNATURE)
                        .algorithm(JWSAlgorithm.RS256)
                        .build())
                .toList();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(new JWKSet(claves)))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .jwtProcessorCustomizer(procesador -> {
                    if (exigirIat) {
                        procesador.setJWTClaimsSetVerifier((claimsSet, contexto) -> {
                            if (claimsSet.getIssueTime() == null) {
                                throw new BadJWTException("iat ausente");
                            }
                        });
                    }
                })
                .build();
        decoder.setJwtValidator(validadorServicio(clavesPorKid.keySet(), validarKid));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validadorServicio(Set<String> kids, boolean validarKid) {
        JwtTypeValidator tipo = new JwtTypeValidator(TIPO.getType());
        tipo.setAllowEmpty(false);
        Duration tolerancia = Duration.ofSeconds(30);
        List<OAuth2TokenValidator<Jwt>> validadores = new ArrayList<>(List.of(
                tipo,
                new JwtIssuerValidator(EMISOR),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audiencias -> audiencias != null && audiencias.contains(AUDIENCIA)),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, SUJETO::equals),
                new JwtTimestampValidator(tolerancia),
                validador("exp", jwt -> jwt.getExpiresAt() != null),
                validador("iat-futuro", jwt -> jwt.getIssuedAt() == null
                        || !jwt.getIssuedAt().isAfter(Instant.now().plus(tolerancia))),
                validador("vida", jwt -> jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plusSeconds(60))),
                new JwtClaimValidator<String>("scope",
                        scope -> scope != null && List.of(scope.split(" ")).stream()
                                .anyMatch(Set.of(PERMISO, PERMISO_PROYECCION)::contains)),
                validador("role", jwt -> !jwt.hasClaim("role"))));
        if (validarKid) {
            validadores.add(validador("kid", jwt -> kids.contains(jwt.getHeaders().get("kid"))));
        }
        return new DelegatingOAuth2TokenValidator<>(validadores);
    }

    /** El error lleva solo el nombre del elemento, para identificar qué validador falló. */
    private static OAuth2TokenValidator<Jwt> validador(String nombre, Predicate<Jwt> condicion) {
        return jwt -> condicion.test(jwt)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", nombre, null));
    }

    // ---- utilidades de prueba ----

    private static Map<String, RSAPublicKey> claves(String kid1, KeyPair par1, String kid2, KeyPair par2) {
        Map<String, RSAPublicKey> claves = new LinkedHashMap<>();
        claves.put(kid1, (RSAPublicKey) par1.getPublic());
        if (kid2 != null) {
            claves.put(kid2, (RSAPublicKey) par2.getPublic());
        }
        return claves;
    }

    private static JWTClaimsSet.Builder validos() {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(EMISOR)
                .audience(AUDIENCIA)
                .subject(SUJETO)
                .claim("scope", PERMISO)
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString());
    }

    private static String firmar(KeyPair par, String kid, JWTClaimsSet claims) {
        return firmar(par, JWSAlgorithm.RS256, kid, TIPO, claims);
    }

    private static String firmar(KeyPair par, JWSAlgorithm algoritmo, String kid, JOSEObjectType tipo,
            JWTClaimsSet claims) {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(algoritmo).keyID(kid).type(tipo).build(), claims);
        try {
            jwt.sign(new RSASSASigner(par.getPrivate()));
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
        return jwt.serialize();
    }

    /** Descripciones de los errores de validación de claims (la firma debe haber sido válida). */
    private static List<String> errores(String token) {
        try {
            DECODER.decode(token);
        } catch (JwtValidationException ex) {
            return ex.getErrors().stream().map(OAuth2Error::getDescription).map(
                    DecodificadorServicioJwtCaracterizacionTest::nombreDelValidador).toList();
        }
        throw new AssertionError("Se esperaba un rechazo por validación de claims");
    }

    /** Traduce las descripciones de los validadores de Spring al nombre del elemento validado. */
    private static String nombreDelValidador(String descripcion) {
        for (String claim : List.of("iss", "aud", "sub", "scope")) {
            if (descripcion.equals("The " + claim + " claim is not valid")) {
                return claim;
            }
        }
        if (descripcion.contains("typ")) {
            return "typ";
        }
        return descripcion;
    }

    private static void assertRechazoDeFirma(String token) {
        assertThatThrownBy(() -> DECODER.decode(token))
                .isInstanceOf(BadJwtException.class)
                .isNotInstanceOf(JwtValidationException.class);
    }
}
