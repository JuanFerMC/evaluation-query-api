# Contrato propuesto para la proyección de lectura de Query

## Alcance de este bloque

Este bloque define y crea el almacenamiento que Query necesita para servir HU13, HU14 y HU15 sin leer tablas de
Scoring. La migración `V2__crear_proyeccion_lectura.sql` crea las tres tablas propuestas en `consulta`. Esta
migración no agrega endpoints ni cambia el comportamiento de la API: la sincronización y el cambio de consultas
se implementarán en bloques separados.

HU14 CA17 exige justificar y aprobar cada tabla o componente de infraestructura nuevo antes de agregarlo. La
propuesta se justificó y recibió aprobación antes de crear la migración.

## Modelo mínimo propuesto

Las tablas vivirían en el esquema `consulta`, que ya pertenece a Query. No tendrían llaves foráneas hacia
`public`: sus IDs son referencias de origen y no deben obligar a Query a consultar el esquema de Scoring.

| Tabla lógica | Clave | Datos guardados | Necesidad |
|---|---|---|---|
| `proyeccion_solicitante` | `id_solicitante` | Solo el identificador interno del solicitante | HU15 debe responder 404 para un solicitante inexistente y 200 con lista vacía para uno existente sin evaluaciones. Sin esta tabla Query tendría que seguir consultando `public.solicitante`. |
| `proyeccion_evaluacion` | `id_evaluacion` | `id_solicitante`, fecha de evaluación, puntaje total nullable, categoría nullable, versión de política nullable y estado nullable | Resumen para HU13/HU15 y estado para excluir pendientes; los null conservan evaluaciones anteriores a HU12/saga. |
| `proyeccion_factor_evaluacion` | (`id_evaluacion`, `orden`) | ID de detalle/regla de origen, variable como snapshot, operador, valor de condición, puntaje de regla, condición cumplida y contribución persistida | HU13 necesita explicar la regla histórica; HU15 necesita derivar puntajes antiguos; HU14 necesita detectar factores desfavorables. |

`proyeccion_evaluacion.id_solicitante` referencia la fila local de `proyeccion_solicitante`, y
`proyeccion_factor_evaluacion.id_evaluacion` referencia la evaluación local. No se crean llaves foráneas a
`public`. No se copian nombre, documento, ingresos, deudas ni otros atributos personales/financieros del
solicitante. El ID del solicitante se conserva internamente para asociar el historial, pero no se agrega a la
respuesta de HU13.

### Restricciones e invariantes

- La identidad del solicitante y de la evaluación conserva el ID generado por Scoring; no se asignan IDs nuevos.
- Puntaje, categoría y versión de política aceptan `NULL` para preservar los registros anteriores a HU12. No se
  reclasifican ni se completan retroactivamente.
- Estado acepta `NULL` para registros anteriores a la saga; para nuevas evaluaciones refleja `PENDIENTE` o
  `COMPLETADA`. Una evaluación solo cambia de pendiente a completada con confirmación de Scoring posterior a
  guardar las recomendaciones, según el flujo vigente de HU14 CA13/CA15.
- Los datos de cada factor son snapshots, nunca referencias a los valores mutables de una regla actual. La
  contribución se copia tal como se persistió (cero cuando no se cumplió la condición).
- Una entrega repetida de la misma evaluación debe producir el mismo snapshot, sin duplicar factores. La
  actualización de estado es idempotente y no permite reescribir los datos históricos de score/factores.
- El orden del historial se calcula con fecha descendente y luego ID de evaluación descendente, conforme HU15.
- Para evaluaciones anteriores a HU12 cuyo puntaje total sea `NULL`, Query deriva el total de los factores y lo
  identifica como derivado, conforme P-HU13-04 y P-HU15-01.

## Datos por historia de usuario

| HU | Lectura desde la proyección | Comportamiento que debe conservarse |
|---|---|---|
| HU13 | Resumen de evaluación y sus factores | Persistidos históricos; suma consistente; 409 si está `PENDIENTE`; sin recalcular ni exponer datos personales/financieros. |
| HU14 | Estado, categoría persistida y factores desfavorables | El catálogo y las recomendaciones siguen siendo propiedad de Query; generación idempotente, versionada e inmutable. Evaluaciones anteriores a la saga no reciben recomendaciones retroactivas. |
| HU15 | Existencia del ID de solicitante, sus evaluaciones y sus factores solo cuando se requiera derivar puntaje antiguo | Solicitante conocido sin evaluaciones: 200 y lista vacía; desconocido: 404; excluir `PENDIENTE`; conservar orden y no incluir datos personales/financieros. |

## Sincronización necesaria

La proyección requiere cubrir dos tipos de dato, no solo evaluaciones:

1. **Existencia de solicitante:** poblar los IDs existentes una vez y sincronizar los nuevos solicitantes. Es
   necesario incluso cuando el solicitante aún no tiene evaluaciones. Al recibir una evaluación, Query debe
   asegurar el registro local de su solicitante antes de guardar el snapshot, dentro de la misma transacción.
2. **Snapshot de evaluación:** enviar la evaluación y todos sus factores en una unidad idempotente. HU14 ya
   tiene una llamada interna de Scoring a Query y un mecanismo durable de reintentos; se debe evaluar si ese
   flujo puede transportar el snapshot y reutilizarse sin crear un broker.
3. **Confirmación de finalización:** Query mantiene `PENDIENTE` hasta que Scoring confirme que guardó las
   recomendaciones y completó la evaluación. La confirmación también debe poder reintentarse después de un
   fallo de red o un reinicio, para no presentar una evaluación como completa prematuramente.
4. **Backfill y reconciliación:** definir una operación controlada para copiar IDs existentes y evaluaciones
   históricas desde `public`, cotejar conteos/IDs y recuperar entregas incompletas. El rol de Query puede perder
   sus permisos de lectura sobre `public` solo después de completar y verificar esta etapa.

El transporte final se definió en [`sincronizacion-proyeccion-query.md`](sincronizacion-proyeccion-query.md): un
outbox local en PostgreSQL sin broker externo, aprobado conforme HU14 CA17. Scoring no debe
escribir tablas del esquema `consulta`; Query debe ser su único escritor. El despliegue debe seguir funcionando
durante la transición, cuando convivan las consultas actuales y la proyección.

## Versionamiento propuesto

La migración `consulta.V2` crea únicamente las tablas de proyección, las restricciones locales y el índice para
listar evaluaciones por solicitante. `consulta.V1` sigue siendo la migración de recomendaciones. La V2 no cambia
las migraciones V1–V109 de `public`, no altera el historial de Scoring/Auth y no hace backfill. El arranque
normal sigue validando Flyway; su aplicación se realiza en despliegue controlado con el rol migrador de Query.

## Justificación de tablas nuevas (HU14 CA17)

- `proyeccion_solicitante` evita la consulta cruzada que hoy distingue 404 de historial vacío. Guarda un único
  identificador mínimo, no datos personales.
- `proyeccion_evaluacion` desacopla las consultas de resumen y estado de la tabla propietaria de Scoring y
  preserva compatibilidad con filas históricas.
- `proyeccion_factor_evaluacion` conserva el snapshot reutilizado por tres funcionalidades, sin duplicar
  lógica de scoring ni depender de reglas editadas posteriormente.

Las tres tablas representan partes diferentes del read model; consolidarlas en JSON o repetir cada factor en
las cabeceras produciría duplicación y dificultaría integridad, consultas por solicitante y migración gradual.
Esta justificación no sustituye la aprobación requerida por HU14 CA17.

## Resultado y trabajo pendiente

- El contrato contiene solo datos necesarios para HU13–HU15, con snapshots históricos y sin PII/atributos
  financieros innecesarios.
- Se reconoce explícitamente la necesidad de proyectar IDs de solicitantes sin evaluaciones.
- El estado `PENDIENTE` no se convierte en `COMPLETADA` hasta la confirmación de Scoring posterior a la
  persistencia de recomendaciones.
- Query ya ofrece operaciones internas para registrar solicitantes y confirmar evaluaciones; quedan pendientes
  el snapshot inicial, el despachador, el backfill y la reconciliación.
- La propuesta de tablas quedó justificada y aprobada conforme HU14 CA17; V2 implementa su estructura.
- V2 no sincroniza datos, no cambia las consultas y no revoca aún permisos de Query sobre `public`.
