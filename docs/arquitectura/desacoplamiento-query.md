# Desacoplamiento de evaluation-query-api

## Propósito y alcance

Este documento registra el diagnóstico inicial y propone una secuencia revisable para que `evaluation-query-api`
sea dueña de sus consultas y recomendaciones sin depender funcionalmente de `scoring-api`. El proyecto mantiene
tres APIs y un frontend, una base PostgreSQL compartida, módulos organizados por funcionalidad y trazas de
arquitectura hexagonal. No se propone una API adicional ni un broker de mensajería.

Este primer bloque es de diagnóstico y documentación: no cambia endpoints, datos, configuración ni migraciones.
Las HUs sirven como criterios de comportamiento; las decisiones de arquitectura descritas aquí son propuestas
para revisar, no instrucciones extraídas de documentos del repositorio.

## Estado observado

- `evaluation-query-api` implementa HU13 (explicación), HU14 (recomendaciones) y HU15 (historial) en módulos
  propios organizados por funcionalidad y puertos/adaptadores.
- Los adaptadores JDBC de HU13, HU15 y la generación HU14 leen tablas de `public` administradas por Scoring:
  `evaluacion`, `detalle_evaluacion`, `regla_scoring`, `riesgo`, `politica_clasificacion` y, para HU15, solo el
  identificador de `solicitante`. Query no recalcula el score ni escribe en las tablas de Scoring.
- La persistencia de recomendaciones ya pertenece a Query y vive en su esquema `consulta`, con migración Flyway
  propia. Sin embargo, la saga de Scoring llama por HTTP al endpoint interno de Query para generarlas y mantiene
  la evaluación `PENDIENTE` mientras espera respuesta y reintentos. Las lecturas de Query también dependen de
  que el esquema de Scoring esté disponible y compatible.
- Auth es el emisor de tokens de usuario mediante RS256. El token actual incluye `iss`, `aud` (por defecto
  `scoring-api` y `evaluation-query-api`), `sub` como UUID público, `email`, `role`, `iat`, `exp` y `jti`.
  Scoring y Query validan la firma localmente con la clave pública y requieren su audiencia. Scoring no tiene un
  módulo que emita tokens de usuario; no hay una funcionalidad de emisión que mover desde Scoring.
- Auth expone `GET /api/v1/auth/validate`, que consulta el estado del usuario. Es diferente de la validación
  criptográfica local que usan Scoring y Query. Consultar Auth en cada petición daría revocación basada en el
  estado actual, pero agregaría dependencia síncrona de disponibilidad; debe mantenerse como decisión explícita.
- Las tres APIs comparten la misma base PostgreSQL. Scoring y Auth usan el esquema común `public` y rangos
  Flyway separados: Scoring V1–V99 y Auth V100 en adelante (actualmente hasta V109). Query usa el esquema
  `consulta` y su propio historial Flyway (V1). No existe un esquema `auth` separado en la configuración actual.

## Dirección propuesta por bloques

1. **Diseñar la proyección de lectura:** definir en `consulta` una proyección versionada con los datos mínimos
   que necesitan HU13 y HU15, más los datos fuente necesarios para que Query genere HU14. Preservar snapshots
   históricos de factores, reglas y clasificación; respetar privacidad y contratos aprobados de las HUs.
2. **Elegir la sincronización sin broker:** definir cómo se entrega una evaluación confirmada desde Scoring
   hasta la proyección de Query con idempotencia, recuperación y consistencia explícitas. Query debe ser el único
   escritor de su esquema. Scoring no escribirá directamente las tablas de Query. Una llamada HTTP síncrona como
   única vía de actualización puede acoplar otra vez la disponibilidad; un outbox local en la base compartida
   podría permitir entrega reintentable sin broker, pero debe evaluarse antes de adoptarlo.
3. **Mover el flujo funcional HU14:** trasladar a Query la decisión de recomendaciones y conservar el catálogo
   y la persistencia allí. El contrato entre APIs debe llevar los datos persistidos necesarios, sin duplicar
   reglas en Scoring ni bloquear la evaluación por la disponibilidad de Query.
4. **Migrar lecturas y retirar permisos:** cambiar HU13–HU15 a leer la proyección, habilitar recuperación y
   reconciliación, y solo entonces retirar SELECT de Query sobre las tablas de Scoring y eliminar la
   dependencia de ciclo de vida de la saga. Desplegar los cambios de manera compatible y por etapas.

La elección entre entrega HTTP reintentable y un outbox sencillo requiere definir quién conserva el trabajo
pendiente, cómo se recuperan fallos y cómo se reconstruye una proyección incompleta. No se recomienda ocultar
lógica de sincronización en triggers ni permitir que dos APIs escriban el mismo esquema.

## Versionamiento y despliegue de base de datos

- Mantener los rangos de Flyway en `public`: Scoring V1–V99 y Auth V100+. No renumerar migraciones aplicadas,
  reutilizar versiones ni crear ahora un esquema Auth distinto sin una decisión de arquitectura aparte.
- Las tablas de lectura de Query y sus migraciones viven en `consulta`, con su historial propio. Las migraciones
  nuevas de Query deben afectar solo a ese esquema; las de Scoring y Auth deben modificar solo sus tablas
  propietarias en `public`.
- Mantener el arranque normal en modo validación. Aplicar migraciones solo en despliegues controlados, con el
  rol migrador correspondiente y comprobaciones previas/posteriores.
- Hacer despliegues compatibles: crear proyección y capacidad de sincronización, poblarla/reconciliarla,
  cambiar las lecturas y, después, revocar permisos antiguos. Evitar exigir un cambio simultáneo de las tres
  APIs.

## Criterios de aceptación transversales

- HU13, HU14 y HU15 conservan sus contratos y escenarios aprobados en `docs/quality/`, incluidos privacidad,
  orden del historial, evaluaciones antiguas y recomendaciones inmutables/versionadas.
- Query lee HU13–HU15 desde datos de su propiedad y no requiere SELECT sobre tablas de Scoring al terminar la
  transición.
- La proyección puede sincronizarse y reconstruirse de forma idempotente, sin broker y sin duplicar reglas de
  negocio en Scoring.
- Auth sigue siendo la única API que emite tokens de usuario; Scoring y Query validan el contrato vigente.
- El versionamiento y la propiedad de tablas quedan claros por esquema y por módulo, con migraciones
  compatibles y documentadas por bloque.
- Cada bloque cambia una funcionalidad acotada, incluye documentación de operación y se presenta para revisión
  antes del commit siguiente.

## Límites de esta revisión

Se revisaron README, configuración, adaptadores JDBC, migraciones, documentos operativos y escenarios HU13–HU15
del clon actual de Query. Se verificaron los contratos actuales de Auth y Scoring descritos arriba. El frontend
queda fuera de este bloque y no se modificó.
