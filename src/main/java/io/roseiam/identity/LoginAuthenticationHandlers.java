package io.roseiam.identity;

import io.roseiam.config.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LoginAuthenticationHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoginAuthenticationHandlers.class);

    private final IdentityService identity;
    private final JsonMapper jsonMapper;

    public LoginAuthenticationHandlers(IdentityService identity, JsonMapper jsonMapper) {
        this.identity = identity;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        try {
            AuthDtos.LoginResponse login = identity.createSessionAfterAuthentication(authentication.getName());
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            jsonMapper.writeValue(response.getOutputStream(), login);
        } catch (DataAccessException exception) {
            logger.error("Could not create authenticated session", exception);
            writeProblem(
                    response,
                    problem(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "authentication_unavailable",
                            "Authentication is temporarily unavailable",
                            request));
        } catch (ApiException exception) {
            writeProblem(response, problem(exception.status(), exception.code(), exception.getMessage(), request));
        } catch (RuntimeException exception) {
            logger.error("Could not create authenticated session", exception);
            writeProblem(
                    response,
                    problem(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "internal_error",
                            "The request could not be completed",
                            request));
        }
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authentication)
            throws IOException, ServletException {
        onAuthenticationSuccess(request, response, authentication);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        ProblemDetail problem;
        if (exception instanceof LoginRequestAuthenticationException invalidRequest) {
            String title = invalidRequest.status() == HttpStatus.UNSUPPORTED_MEDIA_TYPE
                    ? "unsupported_media_type"
                    : "invalid_request";
            problem = problem(invalidRequest.status(), title, invalidRequest.getMessage(), request);
            if (!invalidRequest.violations().isEmpty()) {
                problem.setProperty("violations", invalidRequest.violations());
            }
        } else if (exception instanceof AuthenticationServiceException) {
            logger.error("Login authentication service failed", exception);
            problem = problem(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "authentication_unavailable",
                    "Authentication is temporarily unavailable",
                    request);
        } else {
            try {
                identity.recordAuthenticationFailure();
            } catch (RuntimeException auditException) {
                logger.error("Could not record failed login", auditException);
            }
            problem = problem(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Email or password is invalid", request);
        }
        writeProblem(response, problem);
    }

    private void writeProblem(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:roseiam:problem:" + title));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
