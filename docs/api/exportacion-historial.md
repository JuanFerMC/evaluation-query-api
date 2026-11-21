# Contrato HTTP: exportación de historiales (US 5.2)

## Operaciones

| Formato | Método y ruta | Tipo de respuesta |
|---|---|---|
| PDF | `POST /api/v1/evaluaciones/export/pdf` | `application/pdf` |
| Excel | `POST /api/v1/evaluaciones/export/excel` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

Ambas operaciones requieren autenticación mediante JWT Bearer emitido por `auth-api`. El único rol autorizado es `ANALISTA_CREDITO`. Los administradores y demás roles reciben `403 ACCESO_DENEGADO`.

## Solicitud

Encabezados comunes:

```http
Authorization: Bearer <token>
Content-Type: application/json
Accept: application/pdf
```

Para Excel, el valor de `Accept` debe ser `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.

Cuerpo:

```json
{
  "solicitanteIds": [101, 102, 103]
}
```

El arreglo admite de 1 a 50 identificadores. Un solo ID equivale a exportar el historial de un solicitante; varios IDs producen un único documento con secciones agrupadas por solicitante. Cada sección identifica al solicitante por su nombre completo y conserva su ID; en el PDF, el ID aparece explícitamente en la tarjeta del solicitante.

### Reglas de validación

- `solicitanteIds` es obligatorio y no puede estar vacío.
- No se admiten elementos nulos.
- Cada identificador debe ser mayor que cero.
- No se admiten identificadores duplicados.
- No se admiten más de 50 solicitantes por petición.
- Si uno de los solicitantes no existe, se rechaza la operación completa. No se entrega un documento parcial.

## Respuesta exitosa

El servidor responde con estado `200 OK`, los bytes del documento y estos encabezados:

| Encabezado | PDF | Excel |
|---|---|---|
| `Content-Type` | `application/pdf` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `Content-Disposition` | `attachment; filename=historial-evaluaciones.pdf` | `attachment; filename=historial-evaluaciones.xlsx` |
| `Content-Length` | Longitud del PDF en bytes | Longitud del XLSX en bytes |

Los archivos se generan en memoria. La API no persiste el documento en el sistema de archivos del servidor; el cliente HTTP o navegador administra la descarga y la ubicación de guardado.

## Errores

| Estado | Código | Situación |
|---|---|---|
| 400 | `VALIDATION_ERROR` | JSON malformado, lista vacía, IDs nulos/no positivos/duplicados o más de 50 IDs |
| 401 | `NO_AUTENTICADO` | Token ausente o inválido |
| 403 | `ACCESO_DENEGADO` | Rol no autorizado |
| 404 | `SOLICITANTE_NO_ENCONTRADO` | Al menos un solicitante no existe |
| 500 | `INTERNAL_ERROR` | Error inesperado durante el procesamiento |

Los errores utilizan el sobre común de la API:

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Descripción del error",
  "details": [],
  "traceId": "uuid"
}
```

Las respuestas incluyen el encabezado `X-Trace-Id` conforme a la política transversal de trazabilidad.

## Ejemplos

### Descargar PDF con curl

```bash
curl --request POST "http://localhost:8082/api/v1/evaluaciones/export/pdf" \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/json" \
  --header "Accept: application/pdf" \
  --data '{"solicitanteIds":[101,102]}' \
  --output historial-evaluaciones.pdf
```

### Descargar Excel con curl

```bash
curl --request POST "http://localhost:8082/api/v1/evaluaciones/export/excel" \
  --header "Authorization: Bearer $TOKEN" \
  --header "Content-Type: application/json" \
  --header "Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  --data '{"solicitanteIds":[101,102]}' \
  --output historial-evaluaciones.xlsx
```

### Consumo desde frontend

El frontend debe enviar el JWT en `Authorization`, serializar los IDs seleccionados en `solicitanteIds` y tratar el cuerpo exitoso como un `Blob`. El nombre sugerido puede obtenerse de `Content-Disposition`; si el navegador no expone ese encabezado por CORS, se puede utilizar el nombre fijo documentado arriba. El frontend debe liberar la URL temporal creada para iniciar la descarga cuando ya no la necesite.

## Consideraciones

- La exportación reutiliza el caso de uso de consulta de historial y los puertos de generación PDF/Excel.
- Las evaluaciones en estado `PENDIENTE` se excluyen conforme a las reglas del historial.
- Los reportes incluyen únicamente el nombre completo y el identificador del solicitante para identificar cada sección; no incorporan otros datos personales ni financieros.
- La documentación interactiva OpenAPI/Swagger se encuentra habilitada únicamente en el perfil `dev`, de acuerdo con la configuración de la API.
