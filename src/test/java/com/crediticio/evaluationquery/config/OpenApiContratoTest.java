package com.crediticio.evaluationquery.config;

import com.crediticio.evaluationquery.support.ClavesRsaPrueba;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.security.KeyPair;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato OpenAPI: publicado solo con el perfil dev y coherente con los endpoints reales. */
class OpenApiContratoTest {

    private static final KeyPair CLAVES = ClavesRsaPrueba.generar();

    @TempDir
    static Path directorioClaves;

    @DynamicPropertySource
    static void configurarClavePublica(DynamicPropertyRegistry registro) {
        registro.add("evaluationquery.jwt.clave-publica",
                () -> "file:" + ClavesRsaPrueba.escribirClavePublica(CLAVES, directorioClaves));
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "dev"})
    class ConPerfilDev {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void elContratoDebeDocumentarLosEndpointsYElEsquemaBearer() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/v1/evaluaciones/{idEvaluacion}/explicacion'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/solicitantes/{idSolicitante}/evaluaciones'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/evaluaciones'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/reportes/distribucion-riesgo'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/v1/evaluaciones/{idEvaluacion}/explicacion'].get.responses['401']")
                            .exists())
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andExpect(jsonPath("$.components.schemas.ApiError").exists());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class SinPerfilDev {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void elContratoNoDebePublicarse() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        }
    }
}
