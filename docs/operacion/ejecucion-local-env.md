# Ejecución local de evaluation-query-api

Requisitos: JDK 21, PostgreSQL y la clave pública PEM de Auth. La API escucha en `http://localhost:8082`.

1. Desde PowerShell en la raíz, ejecuta `Copy-Item .env.example .env` y completa credenciales/rutas. La ruta de ejemplo asume que `auth`, `scoring-api` y `evaluation-query-api` son carpetas hermanas; ajusta la ruta si tu estructura es distinta. No copies la clave privada de Auth ni la clave privada de servicio a Query.
2. Query comparte la base de datos con Scoring: su lectura pública consulta tablas de `public` de Scoring y almacena su propio estado en el esquema `consulta`. Deben existir la BD, el esquema y las migraciones requeridas. Flyway solo valida por defecto; el procedimiento controlado para permisos y migraciones está en `docs/operacion/despliegue.md` y `docs/operacion/01-preparar-antes-de-migrar.sql`.
3. Ejecuta `.\run-local.ps1`; carga las variables solo para el proceso de Maven y ejecuta `.\mvnw.cmd spring-boot:run`. La API puede arrancar por separado, pero las consultas necesitan las tablas de Scoring y sus datos.
4. Para el flujo integrado de recomendaciones, habilita `EVALUATION_QUERY_SAGA_HABILITADA=true` aquí y `SCORING_SAGA_HABILITADA=true` en Scoring. Ambas deben compartir issuer y `kid`; Query recibe únicamente la ruta de la clave pública de servicio. Con los repos como carpetas hermanas, `.env` apunta al PEM generado en `../scoring-api/keys-service/scoring-service-public.pem`; no es necesario copiarlo al repo Query. En despliegues separados, distribuye el PEM público de forma segura. Scoring debe apuntar a `http://localhost:8082` y tener la privada de servicio.

El script comprueba variables obligatorias y la existencia de la clave pública de Auth; no realiza migraciones ni altera la base.
