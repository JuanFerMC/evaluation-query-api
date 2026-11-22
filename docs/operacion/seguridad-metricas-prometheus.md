# Acceso seguro a métricas Prometheus

## Alcance

El endpoint `GET /actuator/prometheus` está protegido con HTTP Basic y una cuenta técnica exclusiva para el scraper. Esta autenticación es independiente del JWT de los usuarios de la API.

- `/actuator/health` continúa siendo público y no expone detalles internos.
- Las rutas funcionales mantienen la autenticación JWT y la matriz de roles existente.
- No se habilita acceso anónimo al endpoint de métricas.
- La cuenta técnica no concede permisos sobre rutas funcionales.
- La cadena de métricas solo admite `GET /actuator/prometheus`; los demás métodos se deniegan.
- Si las métricas no están habilitadas, la cadena JWT principal continúa denegando el endpoint.

## Configuración

Definir estas variables en el entorno de ejecución de `evaluation-query-api`:

| Variable | Descripción |
| --- | --- |
| `EVALUATION_QUERY_METRICS_ENABLED` | Debe ser `true` para habilitar la cadena de autenticación de Prometheus. Por defecto es `false`. |
| `EVALUATION_QUERY_METRICS_USERNAME` | Usuario técnico exclusivo de Prometheus. |
| `EVALUATION_QUERY_METRICS_PASSWORD` | Contraseña fuerte y exclusiva para el scraper. |

La aplicación codifica la contraseña en memoria mediante el encoder delegante de Spring Security. El valor original se obtiene del entorno y nunca debe guardarse en el repositorio, en archivos de configuración versionados ni en imágenes Docker.

Si se habilita la autenticación sin configurar usuario o contraseña, la aplicación falla al iniciar en lugar de levantar un endpoint con credenciales vacías.

### Render

En el servicio de `evaluation-query-api`, agregar las tres variables en el apartado de variables de entorno. Guardar usuario y contraseña como secretos y generar una contraseña aleatoria robusta. No incluir sus valores en commits, capturas ni logs.

### Prometheus

Configurar el job de scrape para usar HTTP Basic. Mantener el archivo de configuración y sus secretos fuera del repositorio y restringir sus permisos en el host:

```yaml
scrape_configs:
  - job_name: evaluation-query-api
    metrics_path: /actuator/prometheus
    scheme: https
    static_configs:
      - targets:
          - <host-publico-de-evaluation-query-api>
    basic_auth:
      username: <usuario-tecnico>
      password_file: /etc/prometheus/secrets/evaluation-query-api-password
```

El ejemplo es una plantilla: sustituir el host por el dominio real del servicio y montar el archivo de contraseña como secreto en el entorno de Prometheus. No escribir la contraseña directamente en un archivo versionado.

## Consideraciones para Docker y Kubernetes

- En Docker Compose, preferir una red privada compartida entre Query API y Prometheus; no publicar el puerto de métricas al host si no es necesario.
- Mantener las credenciales en un archivo `.env` local excluido de Git o, preferiblemente, en Docker secrets cuando la configuración lo permita.
- En Kubernetes, migrar las credenciales a un Secret y restringir el acceso de red mediante NetworkPolicy cuando el clúster lo soporte.
- HTTP Basic debe viajar siempre sobre HTTPS cuando el tráfico atraviese una red no confiable. En una red interna de contenedores, limitar el tráfico a los servicios autorizados.
- No usar IP allowlists como único control: las direcciones de salida pueden cambiar y no son una identidad de servicio.

## Verificación manual

Con las variables configuradas y el servicio reiniciado:

```bash
# Sin credenciales: debe responder 401
curl -i https://<host>/actuator/prometheus

# Con credenciales válidas: debe responder 200 y contenido Prometheus
curl -i -u "$EVALUATION_QUERY_METRICS_USERNAME:$EVALUATION_QUERY_METRICS_PASSWORD" \
  https://<host>/actuator/prometheus

# Health continúa público y sin detalles
curl -i https://<host>/actuator/health
```

No pasar contraseñas literales en comandos guardados en el historial del shell. Para pruebas locales, usar variables de entorno o un gestor de secretos.
