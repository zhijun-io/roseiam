package io.roseiam.identity;

import io.roseiam.config.ApiException;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LogoutAuthenticationHandlers implements LogoutHandler, LogoutSuccessHandler {
    private static final Logger logger = LoggerFactory.getLogger(LogoutAuthenticationHandlers.class);
    private static final String FAILURE_ATTRIBUTE = LogoutAuthenticationHandlers.class.getName() + ".failure";

    private final IdentityService identity;
    private final JsonMapper jsonMapper;

    public LogoutAuthenticationHandlers(IdentityService identity, JsonMapper jsonMapper) {
        this.identity = identity;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        try {
            identity.logout(request.getHeader("Authorization"));
        } catch (ApiException exception) {
            request.setAttribute(FAILURE_ATTRIBUTE, exception);
        } catch (DataAccessException exception) {
            logger.error("Could not revoke authenticated session during logout", exception);
            request.setAttribute(
                    FAILURE_ATTRIBUTE,
                    new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "authentication_unavailable",
                            "Authentication is temporarily unavailable"));
        } catch (RuntimeException exception) {
            logger.error("Could not revoke authenticated session during logout", exception);
            request.setAttribute(
                    FAILURE_ATTRIBUTE,
                    new ApiException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "The request could not be completed"));
        }
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        Object failure = request.getAttribute(FAILURE_ATTRIBUTE);
        if (failure instanceof ApiException exception) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
            problem.setTitle(exception.code());
            problem.setType(URI.create("urn:roseiam:problem:" + exception.code()));
            problem.setInstance(URI.create(request.getRequestURI()));
            if (exception.status() == HttpStatus.UNAUTHORIZED) {
                response.setHeader("WWW-Authenticate", "Bearer");
            }
            response.setStatus(exception.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Pragma", "no-cache");
            jsonMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        response.setStatus(HttpStatus.NO_CONTENT.value());
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
