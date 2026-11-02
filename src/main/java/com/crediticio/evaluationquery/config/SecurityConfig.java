package com.crediticio.evaluationquery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Matriz de permisos de evaluation-query-api (HU10, docs/security/matriz-permisos-evaluation-query-api.md).
 * Las consultas de explicación (HU13), recomendaciones (HU14) e historial (HU15) se permiten a ambos roles
 * (HU10 CA04, HU14 CA11); toda otra ruta se deniega salvo /actuator/health. La generación interna de HU14
 * (/internal/**) no la autoriza ningún token de usuario: tiene su propia cadena (SeguridadServicioConfig) y,
 * sin ella, esta la deniega. Cada petición se autentica con su JWT (sin sesión HTTP ni llamadas a auth-api);
 * el rol proviene solo del token.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] RUTAS_SWAGGER = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    /** Swagger solo es público cuando springdoc está habilitado (perfil dev); si no, sus rutas se deniegan. */
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean swaggerHabilitado;

    @Bean
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        RespuestaSeguridadJson respuestas = new RespuestaSeguridadJson();
        http
                // CSRF desactivado intencionalmente: API stateless que autentica exclusivamente
                // con JWT enviado explícitamente en Authorization: Bearer. No se utilizan
                // cookies de autenticación ni sesiones HTTP; el navegador no adjunta credenciales.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers(HttpMethod.GET, "/api/v1/evaluaciones/*/explicacion")
                                .hasAnyRole(JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/evaluaciones/*/recomendaciones")
                                .hasAnyRole(JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/solicitantes/*/evaluaciones")
                                .hasAnyRole(JwtConfig.ROL_ADMINISTRADOR, JwtConfig.ROL_ANALISTA)
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(RUTAS_SWAGGER).access((autenticacion, contexto) ->
                                new AuthorizationDecision(swaggerHabilitado))
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(recursos -> recursos
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(JwtConfig.convertidorAutenticacion()))
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas))
                .exceptionHandling(excepciones -> excepciones
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization", "X-Trace-Id"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        // La API no utiliza cookies de autenticación ni credenciales automáticas del navegador.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
