# evaluation-query-api

API del Motor de Scoring de Riesgo Crediticio para consultar la explicación de una evaluación (HU13), sus
recomendaciones (HU14) y el historial de evaluaciones de un solicitante (HU15), con la autenticación y
autorización transversal de HU10.

Estado (2026-10-22): HU13, HU14 y HU15 implementadas sobre el esquema de `scoring-api` con la saga de
recomendaciones. La proyección V2 está creada y el endpoint HU14 ya recibe snapshots de evaluaciones; el
despachador, el backfill y el cambio de lecturas siguen pendientes. El catálogo de recomendaciones incluido es la **versión 1, APROBADA** por María
Alejandra el 2026-10-19 (P-HU14-01), y la definición de factor desfavorable está confirmada (P-HU14-02).

## Arquitectura

- Aplicación Spring Boot independiente (Java 21, Maven), organizada como monolito modular con Package by
  Feature (`explicacion`, `historial`, `recomendaciones`) y Ports & Adapters.
- Valida localmente los JWT de acceso emitidos por `auth-api`, sin llamarla en cada petición.
- Lee con SELECT (JDBC, `NamedParameterJdbcTemplate`) las tablas de `scoring-api` en la misma base
  PostgreSQL: `evaluacion`, `detalle_evaluacion`, `regla_scoring`, `riesgo`, `politica_clasificacion` y, de
  `solicitante`, solo el identificador. No escribe en `public` y no recalcula evaluaciones.
- HU14: guarda las recomendaciones en su **esquema propio** (`consulta` por defecto), con su propia migración
  Flyway (`db/migration/V1__crear_recomendaciones.sql`): `generacion_recomendaciones` (cabecera, una por
  evaluación; su clave primaria hace idempotentes los reintentos) y `recomendacion_evaluacion` (copia del
  código, el texto y el criterio). Los triggers impiden modificarlas o borrarlas. Flyway de esta API nunca
  migra `public`.
- La generación la invoca la saga de `scoring-api` (operación interna con el token de servicio S1); una
  consulta nunca genera recomendaciones. Cada API confirma su propia transacción; no hay transacción
  distribuida.

### Proyección de lectura (en preparación)

La migración propia V2 crea en `consulta` las tablas `proyeccion_solicitante`, `proyeccion_evaluacion` y
`proyeccion_factor_evaluacion`. Hay operaciones internas idempotentes para registrar solicitantes y confirmar
evaluaciones. Scoring envía el snapshot persistido en la llamada HU14; Query lo guarda y genera recomendaciones
en una transacción local. Las lecturas públicas todavía consultan `public`; el despachador y el backfill siguen
pendientes. El [contrato de la proyección](docs/arquitectura/contrato-proyeccion-evaluaciones.md) y el
[contrato de sincronización](docs/arquitectura/endpoints-internos-proyeccion.md) describen el estado del trabajo.

## Endpoints

Los endpoints públicos exigen `Authorization: Bearer <token>` de `auth-api` con rol `ADMINISTRADOR_RIESGO` o
`ANALISTA_CREDITO` (HU10 CA04). Errores con el formato común `{errorCode, message, details, traceId}`; todas
las respuestas llevan `X-Trace-Id` (se reutiliza el recibido si es un UUID válido).

| Método y ruta | Respuestas |
|---|---|
| `GET /api/v1/evaluaciones/{idEvaluacion}/explicacion` | 200 explicación; 400 `VALIDATION_ERROR`; 401 `NO_AUTENTICADO`; 403 `ACCESO_DENEGADO`; 404 `EVALUACION_NO_ENCONTRADA`; 409 `EVALUACION_PENDIENTE`; 500 `INTERNAL_ERROR` si los datos persistidos son incoherentes |
| `GET /api/v1/evaluaciones/{idEvaluacion}/recomendaciones` | 200 recomendaciones; 400; 401; 403; 404 `EVALUACION_NO_ENCONTRADA`; 409 `EVALUACION_PENDIENTE` |
| `GET /api/v1/solicitantes/{idSolicitante}/evaluaciones` | 200 lista (vacía si no tiene evaluaciones); 400; 401; 403; 404 `SOLICITANTE_NO_ENCONTRADO` |
| `PUT /internal/v1/evaluaciones/{idEvaluacion}/recomendaciones` | **Interna**: solo existe con la saga habilitada y solo la autoriza el token de servicio S1 (ningún token de usuario). Acepta el snapshot JSON de Scoring; lo inserta o verifica sin sobrescribirlo y genera desde ese contenido en una transacción local. Durante el despliegue escalonado aún acepta el cuerpo vacío para instancias antiguas de Scoring. 201 generada; 200 ya existía (reintento); 401; 404; 409 por snapshot inconsistente o evaluación no pendiente; 503 `CATALOGO_NO_DISPONIBLE`. No forma parte del contrato del frontend ni aparece en Swagger |
| `PUT /internal/v1/proyeccion/solicitantes/{idSolicitante}` | **Interna**: token S1 con `proyeccion:actualizar`; 204 (también si ya existía); 400; 401; 403. No forma parte del contrato del frontend |
| `PUT /internal/v1/proyeccion/evaluaciones/{idEvaluacion}/completada` | **Interna**: token S1 con `proyeccion:actualizar`; 204 (actualizada o ya completada); 400; 401; 403; 404 si todavía no se ha proyectado; 409 si el estado histórico es incompatible. No forma parte del contrato del frontend |
| `GET /actuator/health` | Público, sin detalles |
| `GET /v3/api-docs`, `/swagger-ui.html` | Solo con el perfil `dev` (`-Dspring-boot.run.profiles=dev`); si no, 401 |

Explicación (HU13):

```json
{
  "idEvaluacion": 101,
  "fechaEvaluacion": "2026-10-10T10:00:00",
  "scoreTotal": -20,
  "origenPuntaje": "PERSISTIDO",
  "clasificada": true,
  "categoria": "MEDIO",
  "versionPolitica": 1,
  "factores": [
    { "idRegla": 7, "variable": "NUMERO_MORAS", "operadorAplicado": ">", "valorCondicionAplicado": "2",
      "puntajeReglaAplicado": -20, "condicionCumplida": true, "contribucion": -20 },
    { "idRegla": 3, "variable": "INGRESOS_MENSUALES", "operadorAplicado": ">=", "valorCondicionAplicado": "3000000",
      "puntajeReglaAplicado": 20, "condicionCumplida": false, "contribucion": 0 }
  ]
}
```

Recomendaciones (HU14), tal como se generaron (código, texto y versión del catálogo de ese momento). No son
una aprobación ni un rechazo del crédito:

```json
{
  "idEvaluacion": 101,
  "generadas": true,
  "versionCatalogo": "V1",
  "fechaGeneracion": "2026-10-18T14:00:00Z",
  "recomendaciones": [
    { "orden": 1, "codigo": "REC-CAT-MEDIO-01", "texto": "…", "tipoActivacion": "CATEGORIA",
      "categoria": "MEDIO", "variable": null },
    { "orden": 2, "codigo": "REC-MORAS-01", "texto": "…", "tipoActivacion": "FACTOR_DESFAVORABLE",
      "categoria": null, "variable": "NUMERO_MORAS" }
  ]
}
```

En las evaluaciones anteriores a la saga: `generadas: false`, `versionCatalogo` y `fechaGeneracion` en `null`
y lista vacía (P-HU14-04). Selección (P-HU14-02, confirmada): una recomendación por la categoría persistida y una
por cada variable con algún factor desfavorable (condición cumplida con contribución negativa); primero la de
categoría y después las variables en orden alfabético; ante varias entradas para el mismo criterio, la de
código menor.

Historial (HU15), de la más reciente a la más antigua y, a igual fecha, por identificador descendente. Excluye
las evaluaciones PENDIENTE (CA08); `estado` es `COMPLETADA` o `null` (anterior a la saga):

```json
[
  { "idEvaluacion": 101, "fechaEvaluacion": "2026-10-10T10:00:00", "scoreTotal": -20,
    "origenPuntaje": "PERSISTIDO", "clasificada": true, "categoria": "MEDIO", "versionPolitica": 1,
    "estado": "COMPLETADA", "explicacion": "/api/v1/evaluaciones/101/explicacion",
    "recomendaciones": "/api/v1/evaluaciones/101/recomendaciones" }
]
```

- Las reglas se muestran con la instantánea guardada al evaluar (operador, valor y puntaje), no con su valor
  actual. La contribución es 0 cuando la condición no se cumplió.
- Evaluaciones anteriores a HU12: `clasificada: false`, `categoria` y `versionPolitica` en `null`, y
  `scoreTotal` igual a la suma de sus detalles con `origenPuntaje: "DERIVADO_DE_DETALLES"`.
- Las respuestas no incluyen el identificador ni los datos personales o financieros del solicitante.
- Una evaluación PENDIENTE (la saga aún no terminó) responde 409 `EVALUACION_PENDIENTE` en la explicación y
  en las recomendaciones, y no aparece en el historial.

## Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `AUTH_JWT_ISSUER` | Sí | Emisor de los JWT; debe ser el mismo valor configurado en `auth-api` (por defecto `crediticio-auth`) |
| `EVALUATION_QUERY_JWT_PUBLIC_KEY_LOCATION` | Sí | Ubicación de la clave pública RS256 de `auth-api`, p. ej. `file:/ruta/jwt-publica.pem` |
| `EVALUATION_QUERY_DB_URL` | Sí | Base de `scoring-api`, con el formato `postgresql://host:puerto/base` (Spring agrega `jdbc:`) |
| `EVALUATION_QUERY_DB_USERNAME` / `EVALUATION_QUERY_DB_PASSWORD` | Sí | Rol de aplicación: **solo SELECT** sobre Scoring; SELECT/INSERT en tablas propias y UPDATE únicamente de `estado` en `proyeccion_evaluacion` |
| `EVALUATION_QUERY_DB_SCHEMA` | No | Esquema propio de las recomendaciones (por defecto `consulta`); debe existir (Flyway no lo crea) |
| `EVALUATION_QUERY_FLYWAY_MIGRAR_AL_ARRANCAR` | No | `false` por defecto: Flyway solo valida y, con migraciones pendientes, la aplicación no arranca y no aplica nada. `true` solo en un despliegue controlado |
| `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD` | Solo al migrar | Rol propietario del esquema propio, que aplica la migración V1 de esta API |
| `EVALUATION_QUERY_CATALOGO_LOCATION` | No | Catálogo de recomendaciones (por defecto la versión 1 aprobada incluida, `classpath:catalogo/recomendaciones.json`); solo uno con `"estado": "APROBADO"` genera recomendaciones |
| `EVALUATION_QUERY_SAGA_HABILITADA` | No | `true` habilita la operación interna de generación (por defecto `false`) |
| `SCORING_SERVICE_JWT_KEY_ID` / `SCORING_SERVICE_JWT_PUBLIC_KEY_LOCATION` | Con la saga | `kid` y clave pública X.509 del par de servicio de `scoring-api` (mismos valores que allí) |
| `SCORING_SERVICE_JWT_PREVIOUS_KEY_ID` / `SCORING_SERVICE_JWT_PREVIOUS_PUBLIC_KEY_LOCATION` | No | Clave anterior, solo durante una rotación |
| `SCORING_SERVICE_JWT_ISSUER` | No | Emisor del token de servicio (por defecto `scoring-api`) |
| `CORS_ALLOWED_ORIGINS` | No | Orígenes del frontend separados por coma (por defecto `http://localhost:3000`) |
| `EVALUATION_QUERY_API_PORT` | No | Puerto HTTP (por defecto 8082) |

Sin `AUTH_JWT_ISSUER`, sin una clave pública válida, sin la conexión a la base o con un catálogo ilegible la
aplicación no arranca; con la saga habilitada, tampoco sin la clave pública de servicio. La garantía de solo
lectura sobre `scoring-api` es el rol de base de datos: el administrador lo crea con
`docs/operacion/01-preparar-antes-de-migrar.sql` y `02-permisos-despues-de-migrar.sql` (rol, permisos y esquema
propio; procedimiento en `docs/operacion/despliegue.md`). Esos scripts **no se han ejecutado**
sobre `motor_scoring` y requiere autorización.

## Saga de recomendaciones (HU14)

1. `scoring-api` registra la evaluación PENDIENTE con su trabajo de saga (outbox) en una transacción.
2. Después del commit intenta la generación en línea con `PUT /internal/...` y un token de servicio S1 nuevo
   (RS256, `kid`, `typ` `scoring-svc+jwt`, `aud` `evaluation-query-api-internal`, `scope`
   `recomendaciones:generar`, 60 s, sin `role`).
3. Scoring lee la clasificación y los factores ya persistidos y los envía como snapshot. Esta API conserva y
   verifica el snapshot en `consulta`, aplica el catálogo APROBADO y guarda la generación en la misma transacción;
   un reintento debe traer el mismo snapshot y recibe la generación guardada (200), aunque el catálogo haya cambiado.
4. `scoring-api` marca la evaluación COMPLETADA. Si la llamada falla, la evaluación sigue PENDIENTE y el
   reenvío programado reintenta (hasta 10 intentos, espera exponencial de 5 s a 300 s).

Para habilitarla hacen falta un catálogo APROBADO por el negocio, un par RSA de servicio (la privada solo en
`scoring-api` y la pública aquí), `SCORING_SAGA_HABILITADA=true` en `scoring-api` y
`EVALUATION_QUERY_SAGA_HABILITADA=true` aquí.

## Clave pública

- `auth-api` firma los tokens con su clave privada (RS256). La clave privada **solo** se instala en
  `auth-api` y nunca se copia a esta API ni se versiona (`.gitignore` excluye `*.pem`).
- Esta API recibe únicamente la clave pública X.509 en formato PEM (`-----BEGIN PUBLIC KEY-----`) del
  mismo par que usa `auth-api` (su variable `AUTH_JWT_PUBLIC_KEY_LOCATION`). El procedimiento de
  generación del par está descrito en el README de `auth-api`.
- `auth-api` incluye `evaluation-query-api` en la audiencia de sus tokens por defecto
  (`AUTH_JWT_AUDIENCES`); si se cambia esa lista, debe seguir incluyéndola.

## Pruebas

```powershell
.\mvnw.cmd test
```

Las pruebas generan claves RSA efímeras y usan H2 en memoria con una réplica mínima del esquema de
`scoring-api` y del esquema propio (`src/test/resources/esquema-scoring-h2.sql`); no requieren claves reales
ni PostgreSQL. Las de HU14 con la saga habilitada usan un catálogo APROBADO **solo de prueba**
(`src/test/resources/catalogo/recomendaciones-prueba-aprobado.json`), que no es el del negocio.

`EsquemaScoringContratoIT` comprueba las consultas contra el esquema real: aplica las migraciones V1–V11 de
`scoring-api` (carpeta hermana `../scoring-api`) y la V1 propia en un esquema temporal de `motor_scoring_it`,
verifica la inmutabilidad y la concurrencia de las recomendaciones, y elimina el esquema al terminar. Requiere
autorización expresa para usar esa base:

```powershell
.\mvnw.cmd -B test "-Dtest=EsquemaScoringContratoIT" "-Dit.base-dedicada=motor_scoring_it" "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/motor_scoring_it"
```

con `DB_USERNAME` y `DB_PASSWORD` en el entorno. La matriz de permisos está en
`docs/security/matriz-permisos-evaluation-query-api.md` y los requisitos en `docs/quality/`.
