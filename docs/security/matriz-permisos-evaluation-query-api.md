# Matriz de permisos de evaluation-query-api (HU10)

La sección "Comportamiento implementado" está aplicada en
`src/main/java/com/crediticio/evaluationquery/config/SecurityConfig.java` (usuarios) y
`src/main/java/com/crediticio/evaluationquery/config/SeguridadServicioConfig.java` (identidad de servicio de
HU14), y verificada por `SeguridadIntegracionTest`, `ConsultasEvaluacionIntegracionTest`,
`RecomendacionesIntegracionTest` y `ServicioJwtConfigTest`. Las demás secciones describen requisitos y
propuestas todavía no implementados.

Las dos rutas internas nuevas de proyección están implementadas en este bloque; su cobertura de integración
automatizada queda pendiente.

## Autenticación de usuarios

- Cada petición protegida exige un JWT de acceso emitido por `auth-api` en la cabecera
  `Authorization: Bearer <token>`. No hay sesión HTTP ni llamadas a `auth-api`.
- El token se valida localmente con la clave pública (`EVALUATION_QUERY_JWT_PUBLIC_KEY_LOCATION`):
  firma RS256, emisor (`AUTH_JWT_ISSUER`), audiencia que incluya `evaluation-query-api`, `exp`
  obligatorio con 60 s de tolerancia de reloj, `sub` con formato UUID y `role` de texto.
- El rol proviene únicamente del claim `role` del token validado.

## Identidad de servicio (HU14, S1)

- Solo para `/internal/**`, en una cadena de seguridad propia evaluada antes que la de usuarios y que solo
  existe con `EVALUATION_QUERY_SAGA_HABILITADA=true`. Con la saga deshabilitada, `/internal/**` lo deniega la
  cadena de usuarios (401 sin token, 403 con token de usuario).
- Token de `scoring-api` validado localmente con su clave pública, elegida por `kid` (una clave, o dos durante
  una rotación): RS256; `typ` `scoring-svc+jwt`; `kid` configurado (un token sin `kid` se rechaza); `iss` y
  `sub` `scoring-api`; `aud` `evaluation-query-api-internal`; `exp` e `iat` obligatorios (el `iat` se exige en
  Nimbus, antes de que Spring rellene uno ausente); 30 s de tolerancia; `exp − iat` ≤ 60 s; `scope` con
  `recomendaciones:generar` o `proyeccion:actualizar`, según la ruta; sin claim `role`.
- Un token de usuario se rechaza en la ruta interna (401) y un token de servicio se rechaza en las rutas de
  usuario (401): firma, emisor, audiencia y claims son distintos.

## Comportamiento implementado

| Ruta | ADMINISTRADOR_RIESGO | ANALISTA_CREDITO | Sin token o token inválido | Requisito |
| --- | --- | --- | --- | --- |
| `GET /api/v1/evaluaciones/{idEvaluacion}/explicacion` | Permitido | Permitido | 401 | HU13 CA09, HU10 CA04 |
| `GET /api/v1/evaluaciones/{idEvaluacion}/recomendaciones` | Permitido | Permitido | 401 | HU14 CA07, CA11, HU10 CA04 (P-HU14-05) |
| `GET /api/v1/solicitantes/{idSolicitante}/evaluaciones` | Permitido | Permitido | 401 | HU15 CA10, HU10 CA04 |
| `PUT /internal/v1/evaluaciones/{idEvaluacion}/recomendaciones` | Rechazado (401) | Rechazado (401) | 401 | HU14: solo el token de servicio S1 con el permiso `recomendaciones:generar` |
| `PUT /internal/v1/proyeccion/solicitantes/{idSolicitante}` | Rechazado (401) | Rechazado (401) | 401 | Proyección: solo S1 con `proyeccion:actualizar`; idempotente |
| `PUT /internal/v1/proyeccion/evaluaciones/{idEvaluacion}/completada` | Rechazado (401) | Rechazado (401) | 401 | Proyección: solo S1 con `proyeccion:actualizar`; solo `PENDIENTE` → `COMPLETADA` |
| `GET /actuator/health` | Público, sin detalles | Público, sin detalles | Público | Operación |
| Swagger (`/v3/api-docs/**`, `/swagger-ui/**`) | Solo con el perfil `dev` | Solo con el perfil `dev` | Solo con el perfil `dev`; si no, 401 | Operación |
| Cualquier otra ruta o método | 403 | 403 | 401 | Denegada por defecto |

Verificado también con las tres API levantadas y tokens reales de `auth-api` en esquemas temporales de
`motor_scoring_it` (2026-10-18).

## Requisitos aprobados pendientes de implementación (2026-10-18)

- **P-HU10-03:** validación de los tokens conforme al contrato común, con comprobación de `exp`, `sub` y `role`.
  Los detalles que el contrato no define (obligatoriedad de `iat`, `jti` y `kid`, tratamiento de `nbf` y
  composición de la audiencia) siguen pendientes para los tokens de usuario. La sección "Autenticación de
  usuarios" describe las comprobaciones actuales de esta API.

## Propuestas pendientes de decisión

- **P-HU10-04:** relación entre la ventana de 15 minutos de CA10 y los 60 s de tolerancia de reloj sobre `exp`.
- **P-HU14-01** quedó resuelta el 2026-10-19 (catálogo versión 1 aprobado). Sin un catálogo APROBADO, la
  operación interna responde 503 `CATALOGO_NO_DISPONIBLE` y no genera nada.

Códigos de error: `EVALUACION_NO_ENCONTRADA` (P-HU13-07), `SOLICITANTE_NO_ENCONTRADO` (P-HU15-05) y
`EVALUACION_PENDIENTE` (P-HU13-02, P-HU14-03), con el mismo formato que `scoring-api`. La operación interna
usa además `EVALUACION_NO_PENDIENTE` (409) y `CATALOGO_NO_DISPONIBLE` (503), que solo consume la saga.

Requisitos en `docs/quality/HU13-consultar-explicacion-evaluacion.feature`,
`docs/quality/HU14-generar-consultar-recomendaciones.feature` y
`docs/quality/HU15-consultar-historial-evaluaciones.feature`; requisitos transversales de HU10 en
`scoring-api/docs/quality/HU10-autorizar-operaciones-segun-rol.feature`.

## Respuestas de error

| Situación | Estado | `errorCode` |
| --- | --- | --- |
| Sin token, token mal formado, firma inválida, expirado, emisor o audiencia incorrectos, claims obligatorios ausentes | 401 | `NO_AUTENTICADO` |
| Token válido sin permiso para la ruta o con un rol desconocido | 403 | `ACCESO_DENEGADO` |

Ambas respuestas usan el formato `ApiError` e incluyen el `traceId` de la cabecera `X-Trace-Id`.
