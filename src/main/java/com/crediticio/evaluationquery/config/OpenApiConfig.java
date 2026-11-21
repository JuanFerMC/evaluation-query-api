package com.crediticio.evaluationquery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contrato OpenAPI de evaluation-query-api: documenta consultas, reportes y exportaciones del historial.
 * Los endpoints de negocio exigen un JWT de acceso emitido por auth-api (esquema bearerAuth) y pueden
 * responder 401/403 con el formato ApiError. Solo se publica con el perfil dev.
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_SEGURIDAD = "bearerAuth";
    private static final String REFERENCIA_API_ERROR = "#/components/schemas/ApiError";

    @Bean
    public OpenAPI evaluationQueryApiOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("evaluation-query-api - Motor de Scoring de Riesgo Crediticio")
                        .version("v1")
                        .description("API de consulta y reportes del motor de riesgo crediticio: explicación de "
                                + "evaluaciones (HU13), recomendaciones (HU14), historial por solicitante (HU15), "
                                + "búsqueda filtrada y paginada (US 5.1.2), distribución de riesgo (US 7.1.1) y "
                                + "exportación de historiales a PDF y Excel (US 5.2.1 y US 5.2.2). "
                                + "Las exportaciones y la distribución de riesgo son exclusivas de "
                                + "ANALISTA_CREDITO; el historial admite ADMINISTRADOR_RIESGO y ANALISTA_CREDITO. "
                                + "Requiere un JWT de acceso emitido por auth-api. "
                                + "El contrato detallado de exportación está en docs/api/exportacion-historial.md."))
                .components(new Components().addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de acceso RS256 emitido por auth-api")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD));
    }

    @Bean
    public OpenApiCustomizer respuestasDeSeguridad() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(ruta -> ruta.readOperations().forEach(operacion -> {
                if (operacion.getResponses() == null) {
                    operacion.setResponses(new ApiResponses());
                }
                ApiResponses respuestas = operacion.getResponses();
                if (!respuestas.containsKey("401")) {
                    respuestas.addApiResponse("401",
                            respuestaError("Token de acceso ausente, inválido o expirado"));
                }
                if (!respuestas.containsKey("403")) {
                    respuestas.addApiResponse("403",
                            respuestaError("El usuario no tiene un rol autorizado para esta operación"));
                }
            }));
        };
    }

    private static ApiResponse respuestaError(String descripcion) {
        return new ApiResponse()
                .description(descripcion)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref(REFERENCIA_API_ERROR))));
    }
}
