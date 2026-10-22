-- evaluation-query-api, paso 2 de 2: permisos del rol de la aplicación DESPUÉS de aplicar las migraciones V1 y V2
-- propias (recomendaciones y proyección). Lo ejecuta el administrador de la base; no se ha ejecutado sobre
-- motor_scoring. Se ha verificado en esquemas temporales de motor_scoring_it.
--
-- Si la migración la ejecutó otro rol (por ejemplo, el administrador), los objetos creados pasan al propietario
-- del esquema, para que ninguna tabla pertenezca a un superusuario.
--
-- El rol de la aplicación:
--   - lee e inserta recomendaciones (nunca UPDATE ni DELETE: además, los triggers de V1 las hacen inmutables);
--   - escribe solicitantes y snapshots en la proyección; solo puede actualizar el estado de evaluación;
--   - solo lee el historial de Flyway (la aplicación valida las migraciones al arrancar; no migra).
--
-- Uso: psql -h <host> -U <administrador> -d motor_scoring -v rol_app=<rol de la aplicación> \
--        -f 02-permisos-despues-de-migrar.sql
-- Variables opcionales: esquema (por defecto consulta) y base_esperada (por defecto motor_scoring).

\set ON_ERROR_STOP on

\if :{?rol_app}
\else
    \echo 'Falta -v rol_app=<rol de la aplicación>'
    \quit
\endif
\if :{?esquema}
\else
    \set esquema consulta
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

BEGIN;

SELECT set_config('despliegue.esquema', :'esquema', true),
       set_config('despliegue.propietario', pg_get_userbyid(nspowner), true)
FROM pg_namespace WHERE nspname = :'esquema';

DO $$
DECLARE
    v_esquema     text := current_setting('despliegue.esquema');
    v_propietario text := current_setting('despliegue.propietario');
    r             record;
BEGIN
    -- Tablas y vistas (sus índices y secuencias de identidad cambian con ellas)
    FOR r IN
        SELECT format('%I.%I', n.nspname, c.relname) AS nombre, c.relkind
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = v_esquema
          AND c.relkind IN ('r', 'p', 'v', 'm')
          AND c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER %s %s OWNER TO %I',
            CASE r.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'TABLE' END,
            r.nombre, v_propietario);
    END LOOP;
    -- Secuencias independientes (no ligadas a una columna)
    FOR r IN
        SELECT format('%I.%I', n.nspname, c.relname) AS nombre
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = v_esquema AND c.relkind = 'S'
          AND c.relowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i', 'e'))
    LOOP
        EXECUTE format('ALTER SEQUENCE %s OWNER TO %I', r.nombre, v_propietario);
    END LOOP;
    -- Funciones (incluidas las de triggers y las SECURITY DEFINER)
    FOR r IN
        SELECT p.oid::regprocedure AS firma
        FROM pg_proc p
        JOIN pg_namespace n ON n.oid = p.pronamespace
        WHERE n.nspname = v_esquema
          AND p.proowner = (SELECT oid FROM pg_roles WHERE rolname = current_user)
          AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = p.oid AND d.deptype = 'e')
    LOOP
        EXECUTE format('ALTER FUNCTION %s OWNER TO %I', r.firma, v_propietario);
    END LOOP;
END;
$$;

GRANT SELECT, INSERT ON :"esquema".generacion_recomendaciones, :"esquema".recomendacion_evaluacion
    TO :"rol_app";
GRANT SELECT, INSERT ON :"esquema".proyeccion_solicitante, :"esquema".proyeccion_factor_evaluacion
    TO :"rol_app";
GRANT SELECT, INSERT ON :"esquema".proyeccion_evaluacion TO :"rol_app";
GRANT UPDATE (estado) ON :"esquema".proyeccion_evaluacion TO :"rol_app";
GRANT USAGE ON ALL SEQUENCES IN SCHEMA :"esquema" TO :"rol_app";
GRANT SELECT ON :"esquema".flyway_schema_history TO :"rol_app";

COMMIT;

SELECT table_name, string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privilegios
FROM information_schema.role_table_grants
WHERE grantee = :'rol_app' AND table_schema = :'esquema'
GROUP BY table_name
ORDER BY table_name;
