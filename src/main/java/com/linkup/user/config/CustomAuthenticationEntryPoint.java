package com.linkup.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.linkup.user.dto.response.ApiResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        String message = "Unauthorized Access";
        if (authException instanceof BadCredentialsException) {
            message = "Bad credentials";
        }

        ApiResponseDTO<Object> apiResponse = new ApiResponseDTO<>(
                HttpStatus.UNAUTHORIZED.value(),
                message,
                null
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(mapper.writeValueAsString(apiResponse));
    }

}