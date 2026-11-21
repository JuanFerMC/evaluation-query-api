-- evaluation-query-api, paso 1 de 2: preparación ANTES de su primera migración (HU13, HU14, HU15).
-- Lo ejecuta el administrador de la base. NO lo ejecuta la aplicación y no se ha ejecutado sobre
-- motor_scoring: requiere respaldo verificado y autorización expresa (procedimiento en
-- docs/operacion/despliegue.md). Se ha verificado en esquemas temporales de motor_scoring_it.
--
-- Qué hace:
--   1. Crea el rol de la aplicación (LOGIN, sin contraseña en este archivo; NOINHERIT, sin privilegios
--      administrativos).
--   2. Le da SELECT sobre las tablas de scoring-api que consulta; nombre_completo se usa solo para identificar
--      las secciones de los reportes exportados.
--   3. Crea el esquema propio, cuyo propietario es el rol migrador de esta API (si ese rol no existe, lo crea
--      con LOGIN, sin contraseña y sin privilegios administrativos).
--   4. Le da USAGE sobre el esquema propio. Los permisos sobre las tablas que cree la migración V1 se dan
--      después de migrar (02-permisos-despues-de-migrar.sql), para no concederle nada sobre el historial
--      de Flyway salvo lectura.
--
-- Requisitos: las tablas de scoring-api deben existir (V1–V9 aplicadas). V10 y V11 añaden columnas, que
-- el SELECT por tabla ya cubre. El rol migrador no debe ser superusuario.
--
-- Uso (psql, conectado con un rol administrador):
--   psql -h <host> -U <administrador> -d motor_scoring -v rol_app=<rol de la aplicación> \
--        -v rol_migrador=<rol de Flyway de evaluation-query-api> -f 01-preparar-antes-de-migrar.sql
--   y después, de forma interactiva (la contraseña nunca va en archivos ni en argumentos):
--   \password <rol de la aplicación>
-- Variables opcionales: esquema (por defecto consulta), esquema_scoring (por defecto public) y
-- base_esperada (por defecto motor_scoring; el script aborta conectado a cualquier otra base).

\set ON_ERROR_STOP on

\if :{?rol_app}
\else
    \echo 'Falta -v rol_app=<rol de la aplicación>'
    \quit
\endif
\if :{?rol_migrador}
\else
    \echo 'Falta -v rol_migrador=<rol de Flyway de evaluation-query-api>'
    \quit
\endif
\if :{?esquema}
\else
    \set esquema consulta
\endif
\if :{?esquema_scoring}
\else
    \set esquema_scoring public
\endif
\if :{?base_esperada}
\else
    \set base_esperada motor_scoring
\endif

SELECT current_database() = :'base_esperada' AS base_correcta \gset
\if :base_correcta
\else
    \echo 'Conectado a una base distinta de la esperada; no se hace nada'
    \quit
\endif

SELECT NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'rol_migrador') AS crear_migrador \gset

BEGIN;

\if :crear_migrador
CREATE ROLE :"rol_migrador" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
COMMENT ON ROLE :"rol_migrador" IS 'evaluation-query-api: propietario del esquema propio; solo para migraciones';
GRANT CONNECT ON DATABASE :"base_esperada" TO :"rol_migrador";
\endif

-- 1. Rol de la aplicación: sin contraseña aquí; se asigna con \password
CREATE ROLE :"rol_app" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
COMMENT ON ROLE :"rol_app" IS 'evaluation-query-api: lectura de scoring-api y recomendaciones de HU14';
GRANT CONNECT ON DATABASE :"base_esperada" TO :"rol_app";

-- 2. Solo lectura sobre las tablas de scoring-api que consulta (incluye nombre_completo para reportes)
GRANT USAGE ON SCHEMA :"esquema_scoring" TO :"rol_app";
GRANT SELECT ON :"esquema_scoring".solicitante, :"esquema_scoring".riesgo, :"esquema_scoring".regla_scoring,
    :"esquema_scoring".politica_clasificacion, :"esquema_scoring".evaluacion,
    :"esquema_scoring".detalle_evaluacion TO :"rol_app";

-- 3. Esquema propio; Flyway de esta API no lo crea (create-schemas=false)
CREATE SCHEMA :"esquema" AUTHORIZATION :"rol_migrador";
COMMENT ON SCHEMA :"esquema" IS 'evaluation-query-api: recomendaciones generadas (HU14)';

-- 4. Acceso al esquema propio (los permisos sobre sus tablas, después de migrar)
GRANT USAGE ON SCHEMA :"esquema" TO :"rol_app";

COMMIT;

-- Comprobación (sin modificar nada): solo SELECT sobre las tablas de scoring-api
SELECT table_name, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privilegios
FROM information_schema.role_table_grants
WHERE grantee = :'rol_app' AND table_schema = :'esquema_scoring'
GROUP BY table_name
ORDER BY table_name;
