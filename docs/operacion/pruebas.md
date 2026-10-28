# Pruebas de evaluation-query-api

Desde `evaluation-query-api`, ejecutar `mvnw.cmd -B test` (Windows) o `./mvnw -B test` (Linux/macOS).
Las pruebas de API usan H2 en memoria para la proyección de lectura y claves JWT RSA efímeras; no se
conectan a Supabase ni requieren levantar auth o scoring.

`EsquemaScoringContratoIT` valida el contrato real de PostgreSQL y se ejecuta por separado. Requiere
una base dedicada autorizada `motor_scoring_it` y crea un esquema temporal con marca de propiedad;
nunca la apuntes a `public` ni a una base de desarrollo.
