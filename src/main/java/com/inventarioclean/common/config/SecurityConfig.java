package com.inventarioclean.common.config;

import com.inventarioclean.common.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuracion de seguridad de la API.
 *
 * <p>Decisiones:
 * <ul>
 *   <li>Sesion sin estado (STATELESS) con JWT: el token viaja en la cabecera
 *       Authorization, no en cookie, porque el frontend se despliega en
 *       Vercel y el back en Railway (dominios distintos). La alternativa con
 *       cookie HttpOnly quedara en el backend cuando el cliente pida CSRF.</li>
 *   <li>BCrypt con fuerza 12: el estandar para credenciales de usuarios.</li>
 *   <li>{@code @EnableMethodSecurity} habilita {@code @PreAuthorize} para la
 *       autorizacion por rol. La seguridad real se aplica en el servidor;
 *       ocultar botones en el frontend es solo UX.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtFilter = jwtFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())   // API sin estado, sin cookies de sesion
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Publicos: login y health checks de Railway.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()   // solo perfil local
                        // El resto exige token valido; el rol se chequea en
                        // cada metodo con @PreAuthorize.
                        .anyRequest().authenticated()
                )
                // Sin esto, Spring devuelve 403 cuando falta el token. El
                // frontend necesita 401 para limpiar la sesion expirada y
                // devolver al usuario a la pantalla de login.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"error\":\"Sesion no valida o expirada. Ingresa nuevamente.\"}");
                        })
                        // 403 queda reservado para "autenticado pero sin
                        // permiso": ese caso lo reporta @PreAuthorize.
                        .accessDeniedHandler((request, response, deniedException) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(
                                    "{\"error\":\"No tienes permiso para realizar esta accion.\"}");
                        })
                )
                .headers(headers -> headers
                        // H2 usa frames en su consola de desarrollo.
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt con factor de coste 12. Es el estandar para credenciales de
     * usuarios: intentionally lento y con salt por password.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

