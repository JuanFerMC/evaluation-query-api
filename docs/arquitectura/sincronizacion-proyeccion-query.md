# Sincronización propuesta de la proyección de Query

## Decisión que se necesita

La proyección V2 ya tiene almacenamiento, pero no hay una forma fiable de poblarla. Este documento propone un
flujo de entrega sin broker externo. Es un diseño para revisión; **no crea migraciones, endpoints ni procesos**.

## Situación del código actual

- Scoring y Query comparten PostgreSQL. Query lee las tablas de Scoring en `public` durante las peticiones.
- El procesamiento de HU14 ya tiene el outbox `trabajo_recomendacion` en Scoring. Guarda el trabajo pendiente,
  aplica reintentos y solo marca la evaluación `COMPLETADA` después de persistir las recomendaciones.
- La llamada actual a Query lleva el ID de evaluación; Query vuelve a leer el resto del snapshot desde `public`.
- La creación de un solicitante no crea un trabajo durable dirigido a Query. Si Query estuviera fuera de línea,
  no habría forma garantizada de proyectar un solicitante que todavía no tiene evaluaciones.
- HU14 CA13/CA15 exige que un fallo al confirmar la finalización deje la evaluación `PENDIENTE`. Por ello Query
  no puede inferir que quedó completa solo porque guardó las recomendaciones.

## Propuesta: outbox PostgreSQL local, sin broker

Agregar en Scoring un registro de entrega transaccional, en el mismo `public`, para los cambios que deben llegar a
Query:

| Evento | Se registra en la transacción de | Contenido mínimo |
|---|---|---|
| `SOLICITANTE_PROYECTAR` | Alta del solicitante | ID del solicitante |
| `EVALUACION_COMPLETADA` | Confirmación final que marca la evaluación completa | ID de la evaluación y estado `COMPLETADA` |

La primera sincronización y los snapshots completos de evaluación quedan cubiertos por el flujo HU14 existente:
Scoring amplía la llamada de generación para enviar el snapshot persistido (incluida la evaluación `PENDIENTE` y
sus factores). Query lo guarda en V2 y genera las recomendaciones en su transacción local. Un reintento vuelve a
enviar el mismo snapshot; claves y validaciones impiden duplicados o cambios históricos.

El nuevo outbox sería una tabla pequeña con ID de evento, tipo, clave del agregado, payload JSON, fecha de
creación, estado (`PENDIENTE`, `ENTREGADO` o `ERROR_PERMANENTE`), número de intentos, próxima fecha de intento,
concesión temporal y último error acotado. Un proceso de Scoring reclama filas vencidas mediante
`FOR UPDATE SKIP LOCKED`, envía cada evento por HTTP a operaciones internas de Query que se agregarían en un
bloque posterior y
marca la entrega en una transacción local. No mantiene una transacción abierta durante la llamada HTTP. Las
respuestas se aplican de forma idempotente en Query; los eventos repetidos no duplican solicitantes ni
reemplazan snapshots. Los errores transitorios se reintentan y los permanentes quedan disponibles para
intervención operativa.

La API interna de Query debe aceptar el ID de solicitante y la confirmación de estado final. Reutilizar la
identidad S1 existente requiere añadir un permiso específico para actualizar la proyección, separado de
`recomendaciones:generar`, con la misma clave de servicio y audiencia interna. Ningún endpoint de proyección se
expone al frontend ni acepta tokens de usuario.

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

## Versionamiento y despliegue propuestos

- Agregar una migración **V12** en Scoring, porque el historial de `public` llega actualmente a V11. La
  migración crea solo el outbox; no altera tablas de Auth ni las de `consulta`.
- Desplegar primero Query con las operaciones internas compatibles; luego Scoring con la tabla y el emisor; por
  último, poblar y verificar V2 antes de cambiar las lecturas de HU13–HU15.
- El rol de Scoring tendrá los permisos mínimos para insertar/actualizar/reclamar sus eventos. Query no recibe
  permisos de escritura sobre `public`; conserva temporalmente sus permisos SELECT durante el backfill y la
  transición.
- El proceso no se considera activo hasta documentar alarmas o revisión operativa para
  `ERROR_PERMANENTE`, el procedimiento de reintento y la reconciliación de IDs.

## Aprobación requerida antes de implementar

HU14 CA17 exige justificar y aprobar toda tabla/componente nuevo antes de agregarlo. La tabla V12 está
justificada arriba, pero todavía no se ha creado. La aprobación permite implementar el siguiente bloque en
Scoring y Query; si no se aprueba un outbox PostgreSQL, se debe elegir otra estrategia que preserve la entrega
después de una caída sin mantener un SELECT funcional de Query sobre las tablas de Scoring.
