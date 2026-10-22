package com.crediticio.evaluationquery.config;

import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import com.crediticio.evaluationquery.support.TokensPrueba;
import com.crediticio.evaluationquery.support.TokensServicioPrueba;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decodificador de producción del token de servicio S1 (HU14) construido desde sus propiedades. La composición
 * de validadores está caracterizada en DecodificadorServicioJwtCaracterizacionTest.
 */
class ServicioJwtConfigTest {

    private static final KeyPair ACTUAL = ClavesRsaPrueba.generar();
    private static final KeyPair ANTERIOR = ClavesRsaPrueba.generar();

    @TempDir
    Path directorio;

    @Test
    void debeAceptarElTokenDeServicioYRechazarElDeUsuario() throws IOException {
        JwtDecoder decoder = ServicioJwtConfig.decodificador(propiedades("k1", clave(ACTUAL, "actual"), null, null));

        assertThat(decoder.decode(TokensServicioPrueba.firmar(ACTUAL, "k1", TokensServicioPrueba.claimsValidos()
                .build())).getSubject()).isEqualTo("scoring-api");
        assertThatThrownBy(() -> decoder.decode(TokensPrueba.firmar(ACTUAL,
                TokensPrueba.claimsValidos("auth-api", JwtConfig.ROL_ADMINISTRADOR).build())))
                .isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(TokensServicioPrueba.firmar(ACTUAL, null,
                TokensServicioPrueba.claimsValidos().build()))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void conLaClaveAnteriorConfiguradaDebeAceptarAmbasDuranteLaRotacion() throws IOException {
        JwtDecoder decoder = ServicioJwtConfig.decodificador(propiedades("k2", clave(ACTUAL, "actual"), "k1",
                clave(ANTERIOR, "anterior")));

        assertThat(decoder.decode(TokensServicioPrueba.firmar(ACTUAL, "k2", TokensServicioPrueba.claimsValidos()
                .build())).getSubject()).isEqualTo("scoring-api");
        assertThat(decoder.decode(TokensServicioPrueba.firmar(ANTERIOR, "k1", TokensServicioPrueba.claimsValidos()
                .build())).getSubject()).isEqualTo("scoring-api");
        assertThatThrownBy(() -> decoder.decode(TokensServicioPrueba.firmar(ANTERIOR, "k2",
                TokensServicioPrueba.claimsValidos().build()))).isInstanceOf(BadJwtException.class);
    }

    @Test
    void sinKidOClavePublicaLaAplicacionNoArranca() throws IOException {
        Resource clave = clave(ACTUAL, "actual");

        assertThatThrownBy(() -> ServicioJwtConfig.decodificador(propiedades(" ", clave, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("id-clave");
        assertThatThrownBy(() -> ServicioJwtConfig.decodificador(propiedades("k1",
                new FileSystemResource(directorio.resolve("no-existe.pem")), null, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ServicioJwtConfig.decodificador(propiedades("k1", clave, "k1", clave)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("otro kid");
    }

    private Resource clave(KeyPair par, String nombre) throws IOException {
        Path subdirectorio = Files.createDirectories(directorio.resolve(nombre));
        return new FileSystemResource(ClavesRsaPrueba.escribirClavePublica(par, subdirectorio));
    }

    private static ServicioJwtProperties propiedades(String kid, Resource clave, String kidAnterior,
            Resource claveAnterior) {
        return new ServicioJwtProperties("scoring-api", "scoring-api", "evaluation-query-api-internal",
                "recomendaciones:generar", "proyeccion:actualizar", "scoring-svc+jwt", Duration.ofSeconds(30),
                Duration.ofSeconds(60), kid, clave, kidAnterior, claveAnterior);
    }
}
