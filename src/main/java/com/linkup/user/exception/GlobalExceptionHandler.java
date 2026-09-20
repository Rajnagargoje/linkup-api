package com.linkup.user.exception;

import com.linkup.user.dto.response.ApiResponseDTO;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ResourceNotFoundException.class})
    public ResponseEntity<ApiResponseDTO<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({UserAlreadyExistsException.class})
    public ResponseEntity<ApiResponseDTO<Void>> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        logger.info("User already exists: {}", ex.getMessage());
        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(HttpStatus.CONFLICT.value(), ex.getMessage(), null);
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleCustomAuthException(CustomAuthException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponseDTO<>(401, ex.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleSessionError(org.springframework.security.authentication.BadCredentialsException ex) {
        return ResponseEntity.status(401).body(new ApiResponseDTO<>(401, ex.getMessage(), null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ApiResponseDTO<>(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiResponseDTO<>(HttpStatus.BAD_REQUEST.value(), message, null));
    }

    // @Valid failures on @RequestBody DTOs (e.g. RegisterUserDTO, UpdateProfileDTO)
    // were previously falling through to Spring's default error shape,
    // which doesn't match the {status, message, data} contract the
    // frontend expects everywhere else.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(HttpStatus.BAD_REQUEST.value(), message, null);
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    // Last resort. Never leak a stack trace or exception class name to
    // the client — log it server-side, return a generic message.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO<Void>> handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Something went wrong. Please try again.", null);
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
