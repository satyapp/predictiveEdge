package org.predictiveedge.identity.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
public class PlatformSecurityConfiguration {

    @Bean
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, IdentityBearerTokenFilter bearerTokenFilter) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/health", "/actuator/health", "/actuator/health/**",
                                "/api/identity/v1/auth/register",
                                "/api/identity/v1/auth/verify-email",
                                "/api/identity/v1/auth/verify-mobile",
                                "/api/identity/v1/auth/verification/otp/resend",
                                "/api/identity/v1/auth/login").permitAll()
                        .requestMatchers("/api/identity/v1/me", "/api/identity/v1/auth/logout").authenticated()
                        .anyRequest().denyAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(bearerTokenFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
