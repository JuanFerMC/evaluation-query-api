# Sincronización de la proyección de Query

## Decisión

La estrategia aprobada es un outbox local PostgreSQL en Scoring, sin broker externo. Este documento registra el
diseño y su avance; los bloques aún no completados se indican explícitamente.

## Estado actual de implementación

| Parte | Estado |
|---|---|
| Proyección Query V2 | Migración creada; sin backfill y sin lecturas migradas |
| Outbox Scoring V12 | Migración y productores transaccionales creados; migración no aplicada a la base |
| Operaciones internas Query | Registro idempotente de solicitante y transición idempotente a `COMPLETADA` implementados |
| Snapshot inicial de evaluación | Pendiente: la petición HU14 aún debe transportar el snapshot a Query |
| Despachador Scoring | Pendiente |
| Backfill, reconciliación y retiro de SELECT cruzados | Pendientes |

## Situación del código actual

- Scoring y Query comparten PostgreSQL. Query lee las tablas de Scoring en `public` durante las peticiones.
- El procesamiento de HU14 ya tiene el outbox `trabajo_recomendacion` en Scoring. Guarda el trabajo pendiente,
  aplica reintentos y solo marca la evaluación `COMPLETADA` después de persistir las recomendaciones.
- La llamada actual a Query lleva el ID de evaluación; Query vuelve a leer el resto del snapshot desde `public`.
- El alta de solicitante ya registra un evento durable en el outbox V12. El despachador aún no lo envía a Query.
- HU14 CA13/CA15 exige que un fallo al confirmar la finalización deje la evaluación `PENDIENTE`. Por ello Query
  no puede inferir que quedó completa solo porque guardó las recomendaciones.

## Outbox PostgreSQL local, sin broker

Scoring registra en `public.evento_proyeccion_query` los cambios que deben llegar a Query:

| Evento | Se registra en la transacción de | Contenido mínimo |
|---|---|---|
| `SOLICITANTE_PROYECTAR` | Alta del solicitante | ID del solicitante |
| `EVALUACION_COMPLETADA` | Confirmación final que marca la evaluación completa | ID de la evaluación y estado `COMPLETADA` |

La primera sincronización y los snapshots completos de evaluación quedan cubiertos por el flujo HU14 existente:
Scoring amplía la llamada de generación para enviar el snapshot persistido (incluida la evaluación `PENDIENTE` y
sus factores). Query lo guarda en V2 y genera las recomendaciones en su transacción local. Un reintento vuelve a
enviar el mismo snapshot; claves y validaciones impiden duplicados o cambios históricos.

El outbox es una tabla pequeña con ID de evento, tipo, clave del agregado, payload JSON, fecha de
creación, estado (`PENDIENTE`, `ENTREGADO` o `ERROR_PERMANENTE`), número de intentos, próxima fecha de intento,
concesión temporal y último error acotado. Un proceso de Scoring reclama filas vencidas mediante
`FOR UPDATE SKIP LOCKED`, envía cada evento por HTTP a las operaciones internas de Query y marca la entrega en
una transacción local. No mantiene una transacción abierta durante la llamada HTTP. Las
respuestas se aplican de forma idempotente en Query; los eventos repetidos no duplican solicitantes ni
reemplazan snapshots. Los errores transitorios se reintentan y los permanentes quedan disponibles para
intervención operativa.

La API interna de Query acepta el ID de solicitante y la confirmación de estado final. Reutiliza la identidad S1
con el permiso específico `proyeccion:actualizar`, separado de `recomendaciones:generar`, y la misma clave de
servicio y audiencia interna. Ningún endpoint de proyección se expone al frontend ni acepta tokens de usuario.

## Orden y consistencia

1. Cuando Query recibe un snapshot de evaluación, inserta o verifica primero el ID de solicitante en su
   proyección, luego la evaluación y sus factores, y finalmente guarda la generación de recomendaciones. Todo
   queda en una sola transacción de Query.
2. Una repetición del snapshot debe tener el mismo contenido. Si el ID ya existe con un snapshot diferente, se
   rechaza y registra como error de contrato; no se sobreescribe el histórico.
3. La evaluación permanece `PENDIENTE` en la proyección hasta que el evento durable `EVALUACION_COMPLETADA`
   llegue tras el commit final de Scoring. La actualización solo permite `PENDIENTE` → `COMPLETADA` y es
   idempotente.
4. Una caída de Query demora la proyección, pero no pierde el evento ni hace que Scoring vuelva a ejecutar el
   cálculo. El trabajo de recomendaciones conserva su política actual; el evento de finalización se reintenta
   por separado.
5. La carga inicial de V2 usa un procedimiento controlado de backfill y reconciliación. La sincronización
   operativa cubre altas y evaluaciones futuras. Se revocan los SELECT de Query sobre `public` únicamente tras
   cotejar la proyección.

## Justificación (HU14 CA17)

El outbox se justifica por la necesidad de guardar el cambio de negocio y el compromiso de entrega en la misma
transacción de Scoring. Sin ese registro, una caída entre el commit de Scoring y la llamada a Query deja una
proyección incompleta; hacer la llamada HTTP dentro de la transacción tampoco vuelve atómicas dos transacciones
de servicios distintos. El outbox no es un broker ni introduce infraestructura externa: es una tabla de
entrega durable en PostgreSQL, mecanismo ya usado por la saga HU14 para reintentar recomendaciones.

Su alcance se limita a dos eventos que el outbox de recomendaciones no puede representar de forma segura:
solicitantes sin evaluaciones y la confirmación de finalización posterior al guardado de recomendaciones.
Reutilizar `trabajo_recomendacion` para estos casos mezclaría operaciones con ciclos de vida distintos y no
resuelve el alta de un solicitante sin evaluación.

## Versionamiento y despliegue

- La migración **V12** en Scoring crea el outbox; no altera tablas de Auth ni las de `consulta`. Está preparada,
  pero no aplicada a la base.
- Desplegar primero Query con las operaciones internas compatibles; luego Scoring con la tabla y el emisor; por
  último, poblar y verificar V2 antes de cambiar las lecturas de HU13–HU15.
- El rol de Scoring tendrá los permisos mínimos para insertar/actualizar/reclamar sus eventos. Query no recibe
  permisos de escritura sobre `public`; conserva temporalmente sus permisos SELECT durante el backfill y la
  transición.
- El proceso no se considera activo hasta documentar alarmas o revisión operativa para
  `ERROR_PERMANENTE`, el procedimiento de reintento y la reconciliación de IDs.

HU14 CA17 justificó y aprobó el outbox PostgreSQL antes de crear V12. Los bloques actuales implementan
gradualmente esa decisión. El outbox aún no se considera activo en producción: falta aplicar V12, conectar el
despachador y desplegar el receptor de snapshots antes de acumular eventos pendientes.
