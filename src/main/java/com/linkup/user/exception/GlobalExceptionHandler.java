package com.linkup.user.exception;

import com.linkup.user.dto.response.ApiResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {


    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({ ResourceNotFoundException.class })
    public ResponseEntity<ApiResponseDTO<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {

        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(HttpStatus.NOT_FOUND.value(), ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({ UserAlreadyExistsException.class })
    public ResponseEntity<ApiResponseDTO<Void>> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        logger.error("User already exists exception: {}", ex.getMessage());
        ApiResponseDTO<Void> errorResponse = new ApiResponseDTO<>(HttpStatus.CONFLICT.value(), ex.getMessage(),
                null);
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleCustomAuthException(CustomAuthException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponseDTO<>(401, ex.getMessage(), null));
    }

}
