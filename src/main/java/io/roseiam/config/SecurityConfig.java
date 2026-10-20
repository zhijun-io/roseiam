package io.roseiam.config;

import io.roseiam.identity.LoginAuthenticationConverter;
import io.roseiam.identity.LoginAuthenticationHandlers;
import io.roseiam.identity.LogoutAuthenticationHandlers;
import io.roseiam.identity.OpaqueSessionIntrospector;
import jakarta.validation.Validator;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiChain(
            HttpSecurity http,
            OpaqueSessionIntrospector introspector,
            AuthenticationManager authenticationManager,
            JsonMapper jsonMapper,
            Validator validator,
            LoginAuthenticationHandlers loginHandlers,
            LogoutAuthenticationHandlers logoutHandlers,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors)
            throws Exception {
        AuthenticationFilter loginFilter = new AuthenticationFilter(
                authenticationManager, new LoginAuthenticationConverter(jsonMapper, validator));
        loginFilter.setRequestMatcher(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/login"));
        loginFilter.setSuccessHandler(loginHandlers);
        loginFilter.setFailureHandler(loginHandlers);
        loginFilter.setSecurityContextRepository(new NullSecurityContextRepository());
        return http.cors(configuration -> configuration.configurationSource(cors))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/password-resets",
                                "/api/auth/password-resets/completion",
                                "/api/user-invitations/acceptance",
                                "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout.logoutRequestMatcher(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/auth/logout"))
                        .addLogoutHandler(logoutHandlers)
                        .logoutSuccessHandler(logoutHandlers))
                .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .opaqueToken(token -> token.introspector(introspector)))
                .addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(JsonMapper jsonMapper) {
        return (request, response, exception) -> {
            if (hasAuthenticationServiceFailure(exception)) {
                writeProblem(
                        jsonMapper,
                        request,
                        response,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "authentication_unavailable",
                        "Authentication is temporarily unavailable");
                return;
            }
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(
                    jsonMapper, request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        };
    }

    private static boolean hasAuthenticationServiceFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof org.springframework.security.authentication.AuthenticationServiceException)
                return true;
        }
        return false;
    }

    @Bean
    AccessDeniedHandler accessDeniedHandler(JsonMapper jsonMapper) {
        return (request, response, exception) -> writeProblem(
                jsonMapper, request, response, HttpStatus.FORBIDDEN, "forbidden", "Operation not permitted");
    }

    private static void writeProblem(
            JsonMapper jsonMapper,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail)
            throws java.io.IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:roseiam:problem:" + title));
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        List<String> allowlist = properties.getCorsAllowedOrigins().stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (allowlist.contains("*")) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowlist);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}
