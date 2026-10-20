package io.roseiam.config;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handle(ApiException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(exception.status(), exception.code(), exception.getMessage(), request);
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "conflict", "The resource already exists", request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        return validationResponse(violations, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> violations = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field",
                                result.getMethodParameter().getParameterName() == null
                                        ? "parameter"
                                        : result.getMethodParameter().getParameterName(),
                                "message",
                                error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage())))
                .toList();
        return validationResponse(violations, headers, request);
    }

    @Override
    protected ProblemDetail createProblemDetail(
            Exception exception,
            HttpStatusCode status,
            String detailMessageCode,
            String detailMessageArgument,
            Object[] detailMessageArguments,
            WebRequest request) {
        ProblemDetail problem = super.createProblemDetail(
                exception, status, detailMessageCode, detailMessageArgument, detailMessageArguments, request);
        problem.setType(URI.create("urn:roseiam:problem:http-" + status.value()));
        problem.setInstance(URI.create(requestPath(request)));
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled request failure", exception);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "The request could not be completed", request);
        return ResponseEntity.internalServerError().body(problem);
    }

    private ResponseEntity<Object> validationResponse(
            List<Map<String, String>> violations, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("invalid_request");
        problem.setType(URI.create("urn:roseiam:problem:invalid_request"));
        problem.setInstance(URI.create(requestPath(request)));
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:roseiam:problem:" + title));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    private static String requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return "/";
    }
}
