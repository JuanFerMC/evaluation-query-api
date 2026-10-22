# Operaciones internas de la proyección

## Alcance de este bloque

Query recibe dos eventos del outbox Scoring en operaciones internas protegidas. No lee ni escribe `public`; su
único almacenamiento afectado es su proyección en `consulta`. Estas rutas aún no tienen despachador conectado.

## Contrato

La ruta HU14 `PUT /internal/v1/evaluaciones/{idEvaluacion}/recomendaciones` también acepta el snapshot de
Scoring en el cuerpo JSON. Incluye la cabecera (ID de evaluación y solicitante, fecha, puntaje, categoría, versión
de política y estado) y la lista ordenada de factores persistidos (IDs de detalle y regla, variable, condición
aplicada, puntaje, resultado y contribución). No incluye datos personales o financieros del solicitante. El ID
del cuerpo debe coincidir con el de la ruta, el estado debe ser `PENDIENTE` y el puntaje debe ser igual a la suma
de contribuciones. Query inserta el snapshot local y genera recomendaciones desde el mismo objeto dentro de una
transacción; en reintentos exige igualdad exacta y responde 409 si el contenido histórico difiere. Por compatibilidad
temporal, un cuerpo vacío sigue usando el lector anterior desde `public` hasta retirar las instancias antiguas de
Scoring.

| Método y ruta | Resultado | Idempotencia |
|---|---|---|
| `PUT /internal/v1/proyeccion/solicitantes/{idSolicitante}` | `204 No Content`; `400 VALIDATION_ERROR` si el ID no es positivo | Inserta el ID o no hace nada si ya existe |
| `PUT /internal/v1/proyeccion/evaluaciones/{idEvaluacion}/completada` | `204 No Content`; `404 EVALUACION_PROYECCION_NO_ENCONTRADA` si falta; `409 ESTADO_PROYECCION_INCOMPATIBLE` si el estado no admite la transición | Cambia `PENDIENTE` a `COMPLETADA`; repetir sobre `COMPLETADA` devuelve 204 |

La confirmación no crea una evaluación faltante ni completa filas históricas con estado `NULL`. Esto evita
inventar snapshots o reinterpretar datos anteriores a la saga. Si llega antes del snapshot, responde 404; el
despachador deberá reintentar esa entrega, pues el snapshot puede estar terminando de guardarse en la llamada
HU14.

## Seguridad

Las rutas solo existen con `EVALUATION_QUERY_SAGA_HABILITADA=true` y usan la cadena interna S1, no la cadena de
JWT de usuarios. El token requiere la clave pública de Scoring, emisor, sujeto, audiencia, expiración, emisión,
`kid`, `typ` y ausencia de `role`. Cada ruta exige su propio `scope`:

- Generación HU14: `recomendaciones:generar`.
- Actualización de proyección: `proyeccion:actualizar`.

El emisor S1 es Scoring y firma con su propia clave de servicio. Auth no participa en esta integración ni requiere
cambios. Ninguna ruta se agrega al contrato del frontend ni a Swagger.

## Consistencia

Cada operación se ejecuta en una transacción local de Query. El alta usa `INSERT ... ON CONFLICT DO NOTHING`. La
confirmación realiza una actualización condicionada por el estado previo y, si no cambia filas, distingue el
reintento ya completado del ID ausente o de un estado histórico incompatible. Query es el único escritor de la
proyección; el siguiente bloque debe enviar el snapshot inicial antes de habilitar la entrega de eventos de
finalización.
