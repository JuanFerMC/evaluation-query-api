# Contratos HTTP de evaluation-query-api

La especificación OpenAPI se genera desde las anotaciones del código y se habilita únicamente con el perfil
`dev`. El contrato en ejecución es la fuente de verdad para métodos, rutas, parámetros, esquemas y respuestas.

## Historias y contratos detallados

| Historia | Operación | Documentación |
|---|---|---|
| US 5.1.2 – Filtrar historial | `GET /api/v1/evaluaciones` | Anotaciones OpenAPI de `BusquedaHistorialController` y README |
| US 5.2.1 – Exportar historial a PDF | `POST /api/v1/evaluaciones/export/pdf` | [Contrato de exportación](exportacion-historial.md) |
| US 5.2.2 – Exportar historial a Excel | `POST /api/v1/evaluaciones/export/excel` | [Contrato de exportación](exportacion-historial.md) |
| US 7.1.1 – Distribución por riesgo | `GET /api/v1/reportes/distribucion-riesgo` | Anotaciones OpenAPI de `ReportesController` y README |
| HU13–HU15 | Explicación, recomendaciones e historial por solicitante | README y documentos en [quality](../quality/) |

## Seguridad y operación

- Las rutas de usuario requieren JWT Bearer emitido por `auth-api`.
- La matriz de permisos vigente está en [matriz de permisos](../security/matriz-permisos-evaluation-query-api.md).
- La documentación interactiva está restringida al perfil `dev`; no se debe habilitar Swagger públicamente en producción.
- Las rutas `/internal/**` son contratos entre servicios y no forman parte del contrato público del frontend. Sus detalles están en los documentos de [arquitectura](../arquitectura/).

## Regla de mantenimiento

Al agregar o cambiar un endpoint, actualizar las anotaciones OpenAPI, las pruebas de contrato y el documento de esta sección cuando corresponda. Evitar duplicar reglas detalladas: mantener un único documento contractual por funcionalidad y enlazarlo desde README y Swagger.
