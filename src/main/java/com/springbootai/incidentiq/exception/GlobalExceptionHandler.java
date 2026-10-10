package com.springbootai.incidentiq.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;

/**
 * Maps exceptions to RFC 9457 problem responses ({@code application/problem+json}).
 * Spring MVC errors (malformed JSON, type mismatches, unsupported methods, ...) are handled
 * by {@link ResponseEntityExceptionHandler}, so every error shares the same JSON shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceNotFoundException.class)
    ProblemDetail handleNotFound(ServiceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", e.getMessage());
    }

    @ExceptionHandler(DuplicateServiceNameException.class)
    ProblemDetail handleDuplicate(DuplicateServiceNameException e) {
        return problem(HttpStatus.CONFLICT, "Duplicate resource", e.getMessage());
    }

    @ExceptionHandler(InvalidServiceException.class)
    ProblemDetail handleInvalid(InvalidServiceException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        log.error("Unexpected error while handling request", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "One or more fields are invalid");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    /** Uses a fixed detail so the rejected input is not echoed back in the response. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String parameter = e.getPropertyName() != null ? e.getPropertyName() : "parameter";
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Parameter '" + parameter + "' has an invalid format");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
