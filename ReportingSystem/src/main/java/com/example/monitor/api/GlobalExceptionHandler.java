package com.example.monitor.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns errors of every REST controller into one JSON shape, {"error": "..."}:
 * 400 for a wrong request, 503 when the database cannot be reached, Spring's own status for web errors
 * such as an unknown URL (404), and 500 for anything else.
 * The details of 503 and 500 go only to the log, never to the caller.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** A parameter of the wrong type, e.g. limit=abc, tier=TERRIBLE or a unit id that is not a number. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> wrongType(MethodArgumentTypeMismatchException e) {
        return error("Parameter '" + e.getName() + "' has an invalid value '" + e.getValue() + "'");
    }

    /** The JSON body of a POST cannot be read. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> unreadableBody(HttpMessageNotReadableException e) {
        return error("The request body is not valid JSON, expected {\"by\": \"...\", \"note\": \"...\"}");
    }

    /** A required parameter is missing. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> missingParameter(MissingServletRequestParameterException e) {
        return error("Parameter '" + e.getParameterName() + "' is required");
    }

    /** The service rejects bad limits and unknown faults with this exception. */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badArgument(IllegalArgumentException e) {
        return error(e.getMessage() == null ? "Bad request" : e.getMessage());
    }

    /** A single unit was asked for and does not exist. */
    @ExceptionHandler(UnitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> unitNotFound(UnitNotFoundException e) {
        return error(e.getMessage());
    }

    /** The database is down or a query failed. */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> database(DataAccessException e) {
        log.error("Database error", e);
        return error("Database is not available");
    }

    /**
     * Anything else. Spring's own web errors (unknown URL = 404, wrong HTTP method = 405 ...) keep their status;
     * a real failure becomes 500 and its stack trace is logged.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        if (e instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String reason = springError.getBody().getDetail();
            return ResponseEntity.status(status).body(error(reason == null ? "Request failed" : reason));
        }
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("Internal error"));
    }

    private static Map<String, String> error(String message) {
        return Map.of("error", message);
    }
}
