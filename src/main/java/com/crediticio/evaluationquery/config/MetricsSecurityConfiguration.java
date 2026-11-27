package com.crediticio.evaluationquery.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;

/**
 * Protege exclusivamente el endpoint de scrape de Prometheus.
 *
 * La cuenta técnica es independiente de los JWT de usuario y solo existe cuando
 * evaluationquery.metrics.enabled=true. Si no se habilita, SecurityConfig mantiene
 * su política denyAll para /actuator/prometheus.
 */
@Configuration
@ConditionalOnProperty(
        prefix = "evaluationquery.metrics",
        name = "enabled",
        havingValue = "true"
)
public class MetricsSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain metricsSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/prometheus")
                // El endpoint solo admite lectura y autenticación Basic explícita.
                // No usa cookies ni sesiones, por lo que CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    UserDetailsService metricsUserDetailsService(
            @Value("${evaluationquery.metrics.username:}") String username,
            @Value("${evaluationquery.metrics.password:}") String password,
            PasswordEncoder metricsPasswordEncoder
    ) {
        Assert.hasText(username, "Debe configurarse EVALUATION_QUERY_METRICS_USERNAME cuando las métricas están habilitadas");
        Assert.hasText(password, "Debe configurarse EVALUATION_QUERY_METRICS_PASSWORD cuando las métricas están habilitadas");

        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(metricsPasswordEncoder.encode(password))
                        .roles("PROMETHEUS")
                        .build()
        );
    }

    @Bean
    PasswordEncoder metricsPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
