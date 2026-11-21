# Integración del frontend con las tres API

Guía elaborada el 2026-10-18 a partir del código de `auth-api`, `scoring-api` y `evaluation-query-api`.
Todo lo que aparece aquí está tomado de la configuración y los controladores reales, salvo lo marcado como
**no verificado**. El flujo descrito se ejecutó de extremo a extremo con las tres API levantadas a la vez y
tokens reales de `auth-api`, en esquemas temporales de `motor_scoring_it` y con un catálogo de
recomendaciones APROBADO solo de prueba (ver "Limitaciones").

## 1. Servicios locales

| API | Puerto por defecto | Variable del puerto | Base de datos | Swagger |
|---|---|---|---|---|
| `auth-api` | 8081 | `AUTH_API_PORT` | Esquema `auth` (Flyway propio; el esquema debe existir) | Solo con el perfil `dev` |
| `scoring-api` | 8080 (Spring Boot por defecto) | — | Esquema `public` (Flyway V1–V11) | Solo con el perfil `dev` (`/swagger-ui.html`) |
| `evaluation-query-api` | 8082 | `EVALUATION_QUERY_API_PORT` | Lee `public` (solo SELECT); escribe sus recomendaciones en su esquema propio (`consulta`, Flyway propio) | Solo con el perfil `dev` (`/swagger-ui.html`) |

Las tres comparten la misma instancia y base PostgreSQL (`motor_scoring` en el entorno local actual).

## 2. Autenticación

1. `POST http://localhost:8081/api/v1/auth/login` con `{"correo": "...", "contrasena": "..."}` →
   200 `{"accessToken": "...", "tokenType": "Bearer", "expiresIn": 900}` y la cookie de renovación
   `refresh_token` (HttpOnly, ruta `/api/v1/auth`). 401 `CREDENCIALES_INVALIDAS`.
2. Cada llamada a `scoring-api` y `evaluation-query-api` envía `Authorization: Bearer <accessToken>`.
3. El token dura 15 minutos. Para renovarlo: `POST /api/v1/auth/refresh` con la cookie y un `Origin`
   permitido (la petición del navegador debe usar `credentials: "include"`). Cierre: `POST /api/v1/auth/logout`.
4. El rol viaja en el claim `role` (`ADMINISTRADOR_RIESGO` o `ANALISTA_CREDITO`). El frontend puede leerlo
   del token para mostrar u ocultar opciones, pero la autorización real la aplica cada API.

Usuarios de prueba: el primer administrador se crea con el procedimiento offline del perfil
`bootstrap-admin` de `auth-api` (README de `auth-api`, "Primer administrador"); después, ese administrador
registra usuarios con `POST /api/v1/usuarios` `{"correo", "contrasena", "rol"}` (contraseña de 12 caracteres
como mínimo). No existen credenciales por defecto.

## 3. Contrato común

- Errores: `{"errorCode": "...", "message": "...", "details": [...], "traceId": "..."}`.
- 401 `NO_AUTENTICADO` (sin token, inválido o expirado); 403 `ACCESO_DENEGADO` (rol sin permiso).
- `X-Trace-Id`: el frontend puede enviar un UUID; si no lo envía o no es válido, la API genera uno. Todas las
  respuestas lo devuelven y la cabecera está expuesta por CORS.
- Fechas de evaluación en formato ISO-8601 local, por ejemplo `2026-10-10T10:00:00`; `fechaGeneracion` de las
  recomendaciones lleva desplazamiento horario (`2026-10-18T14:00:00Z`).

## 4. CORS

Origen permitido por defecto en las tres API: `http://localhost:3000` (`CORS_ALLOWED_ORIGINS`, separados por coma).

| API | Métodos | Credenciales (cookies) |
|---|---|---|
| `auth-api` | GET, POST, OPTIONS | Sí (necesario para la cookie de renovación) |
| `scoring-api` | GET, POST, PATCH, OPTIONS | No |
| `evaluation-query-api` | GET, OPTIONS | No |

Cabeceras admitidas: `Content-Type`, `Accept`, `Authorization`, `X-Trace-Id`.

## 5. Endpoints disponibles

### auth-api (8081)

| Método y ruta | Acceso |
|---|---|
| `POST /api/v1/auth/login` | Público |
| `POST /api/v1/auth/refresh` | Cookie de renovación + `Origin` permitido |
| `POST /api/v1/auth/logout` | Cookie de renovación + `Origin` permitido |
| `POST /api/v1/usuarios` | `ADMINISTRADOR_RIESGO` |

### scoring-api (8080)

| Método y ruta | Cuerpo | Roles (implementados) |
|---|---|---|
| `POST /api/v1/solicitantes` | `nombreCompleto`, `numeroDocumento`, `ingresosMensuales`, `deudasMensuales`, `numeroMoras`, `historialCrediticio` (`BUENO`/`REGULAR`/`MALO`), `antiguedadLaboral` | ANALISTA |
| `GET /api/v1/solicitantes/documento/{numeroDocumento}` | — | Ambos |
| `POST /api/v1/variables-riesgo` | `variable`, `descripcion` | ADMINISTRADOR |
| `PATCH /api/v1/variables-riesgo/{idRiesgo}/estado` | `estado` (`ACTIVA`/`INACTIVA`) | ADMINISTRADOR |
| `POST /api/v1/reglas-scoring` | `idRiesgo`, `operador` (`=`, `>`, `>=`, `<`, `<=`), `valorCondicion`, `puntaje` (-100 a 100) | ADMINISTRADOR |
| `PATCH /api/v1/reglas-scoring/{idRegla}` | `operador`, `valorCondicion`, `puntaje` | ADMINISTRADOR |
| `GET /api/v1/politicas-clasificacion/vigente` | — | Ambos |
| `POST /api/v1/politicas-clasificacion` | `rangos`: lista de `{categoria (BAJO/MEDIO/ALTO), limiteInferior, limiteSuperior}`; deben cubrir de -100 a 130 sin huecos | ADMINISTRADOR |
| `POST /api/v1/evaluaciones` | `idSolicitante` o `numeroDocumento` | ANALISTA; 201 con `scoreTotal`, `categoria`, `idPolitica`, `versionPolitica`, `estado` y `detalles` |
| `GET /api/v1/evaluaciones/{idEvaluacion}` | — | Ambos (P-HU10-01) |

Sin política vigente, `POST /api/v1/evaluaciones` responde 409 `POLITICA_VIGENTE_NO_DISPONIBLE`.

`estado` de la evaluación (saga de HU14): `COMPLETADA` cuando las recomendaciones ya están guardadas;
`PENDIENTE` mientras no lo están (por ejemplo, si `evaluation-query-api` no respondía); `null` en evaluaciones
anteriores a la saga. Una evaluación PENDIENTE pasa sola a COMPLETADA cuando el reenvío programado lo consigue:
el frontend puede volver a consultar `GET /api/v1/evaluaciones/{id}` más tarde.

### evaluation-query-api (8082)\n\n| `GET /api/v1/evaluaciones` | Ambos | Historial filtrado y paginado (US 5.1.2); filtros opcionales `fechaDesde`, `fechaHasta`, `numeroDocumento`, `categoria`; `pagina` desde 0 y `tamano` de 1 a 100. Excluye `PENDIENTE`; incluye `totalRegistros` y `totalPaginas` |\n

| Método y ruta | Roles | Respuesta |
|---|---|---|
| `GET /api/v1/evaluaciones/{idEvaluacion}/explicacion` | Ambos | Explicación (HU13); 404 `EVALUACION_NO_ENCONTRADA`; 409 `EVALUACION_PENDIENTE` |
| `GET /api/v1/evaluaciones/{idEvaluacion}/recomendaciones` | Ambos | Recomendaciones (HU14); 404 `EVALUACION_NO_ENCONTRADA`; 409 `EVALUACION_PENDIENTE`; `generadas: false` y lista vacía en evaluaciones anteriores a la saga |
| `GET /api/v1/solicitantes/{idSolicitante}/evaluaciones` | Ambos | Historial (HU15) sin las PENDIENTE; 404 `SOLICITANTE_NO_ENCONTRADO`; `[]` si no tiene evaluaciones |

Formatos de respuesta en el README de `evaluation-query-api`. Cada elemento del historial incluye `estado` y,
en `explicacion` y `recomendaciones`, las rutas relativas para consultarlas (se resuelven contra la URL base de
`evaluation-query-api`). Las recomendaciones no son una aprobación ni un rechazo del crédito.

## 6. Flujo recomendado para probar desde el frontend

1. Iniciar sesión como administrador (`auth-api`) y, si no existe, publicar una política de clasificación
   (`scoring-api`); crear variables de riesgo y reglas de scoring.
2. Iniciar sesión como analista; registrar o buscar un solicitante (`scoring-api`).
3. Ejecutar la evaluación (`POST /api/v1/evaluaciones`): devuelve puntaje, categoría, versión de la política y
   `estado`.
4. Si `estado` es `COMPLETADA`, consultar en `evaluation-query-api` la explicación, las recomendaciones y el
   historial del solicitante. Si es `PENDIENTE`, mostrar que el resultado aún no está completo y volver a
   consultar la evaluación más tarde.

## 7. Arranque local

Variables comunes: `AUTH_JWT_ISSUER` (igual en las tres API) y `CORS_ALLOWED_ORIGINS`.

| API | Variables propias |
|---|---|
| `auth-api` | `AUTH_DB_URL`, `AUTH_DB_USERNAME`, `AUTH_DB_PASSWORD`, `AUTH_JWT_KEY_ID`, `AUTH_JWT_PRIVATE_KEY_LOCATION`, `AUTH_JWT_PUBLIC_KEY_LOCATION` (opcionales: `AUTH_JWT_AUDIENCES`, `AUTH_ALLOWED_ORIGINS`, `AUTH_COOKIE_SECURE`) |
| `scoring-api` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SCORING_JWT_PUBLIC_KEY_LOCATION`; con la saga: `SCORING_SAGA_HABILITADA=true`, `EVALUATION_QUERY_API_BASE_URL`, `SCORING_SERVICE_JWT_KEY_ID`, `SCORING_SERVICE_JWT_PRIVATE_KEY_LOCATION`, `SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION` |
| `evaluation-query-api` | `EVALUATION_QUERY_DB_URL`, `EVALUATION_QUERY_DB_USERNAME`, `EVALUATION_QUERY_DB_PASSWORD`, `EVALUATION_QUERY_JWT_PUBLIC_KEY_LOCATION`, `SPRING_FLYWAY_USER`, `SPRING_FLYWAY_PASSWORD` (opcional `EVALUATION_QUERY_DB_SCHEMA`); con la saga: `EVALUATION_QUERY_SAGA_HABILITADA=true`, `SCORING_SERVICE_JWT_KEY_ID`, `SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION`, `EVALUATION_QUERY_CATALOGO_LOCATION` (catálogo APROBADO) |

Orden: 1) base de datos con los esquemas `auth` y `consulta` y el rol de `evaluation-query-api` creados
(`evaluation-query-api/docs/operacion/01-preparar-antes-de-migrar.sql`); 2) `auth-api` (aplica su Flyway);
3) `scoring-api` (V1–V11 en `public`); 4) `evaluation-query-api` (su V1 en `consulta`; necesita las tablas de
`scoring-api`). `scoring-api` y `evaluation-query-api` solo migran con `SCORING_FLYWAY_MIGRAR_AL_ARRANCAR=true` o
`EVALUATION_QUERY_FLYWAY_MIGRAR_AL_ARRANCAR=true`; sin ellas, con migraciones pendientes no arrancan. En una base
local nueva hay que arrancarlas una vez con esa variable. Las dos API consumidoras usan la clave **pública** del par de `auth-api`; la
privada solo la tiene `auth-api`. El par de servicio de la saga es otro: su privada solo la tiene
`scoring-api` y su pública, `evaluation-query-api`. En local, `AUTH_COOKIE_SECURE=false` puede ser necesario si
el frontend se sirve por HTTP (no verificado).

## 8. Bloqueos y limitaciones

- **Catálogo de recomendaciones (P-HU14-01):** versión 1 aprobada el 2026-10-19. Con la saga deshabilitada
  (valor por defecto) las evaluaciones nuevas quedan PENDIENTE: en local hay que habilitarla en
  `scoring-api` y en `evaluation-query-api`.
- **`motor_scoring`:** desplegado el 2026-10-19 (V11, esquema `consulta` y roles de ejecución). Faltan las
  contraseñas de `scoring_app` y `consulta_app` y las claves de la saga (ver
  `scoring-api/docs/operacion/despliegue-v10-v11.md`). No hay ninguna política publicada y, con las reglas
  actuales, una evaluación puede sumar más de 130 puntos y responder 409 `PUNTAJE_SIN_CLASIFICACION`.
- **Verificado el 2026-10-19 (entorno aislado):** cookies de renovación en Chrome 155 (login, renovación con
  cookie Secure/HttpOnly/SameSite=Strict, token renovado aceptado por `evaluation-query-api`, cierre de sesión);
  CA12 con unas 1.400 evaluaciones: p95 entre 15 y 35 ms y máximo de 80 ms en explicación, recomendaciones e
  historial (incluido un historial de 200 evaluaciones).
- **No verificado:** tiempos en el servidor definitivo y con la red real.
