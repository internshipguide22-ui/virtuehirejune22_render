package com.virtuehire.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .cors(cors -> {})

            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/auth/**",
                            "/api/public/**",
                            "/api/hrs/**",
                            "/api/admin/**",
                            "/api/jobs/**",
                            "/api/candidates/**",
                            "/api/assessment/**",
                            "/api/questions/**",
                            "/api/monitoring/**",
                            "/api/payments/**",
                            "/api/ws-assessment/**",
                            "/ws-assessment/**"
                    ).permitAll()

                    .anyRequest().authenticated()
            );

        return http.build();
    }
}
