package com.edunetcracker.startreker.advice;

import com.edunetcracker.startreker.controllers.exception.RequestException;
import com.edunetcracker.startreker.dto.ValidationExceptionDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ExceptionsAdvice {

    @ExceptionHandler(BindException.class)
    public ResponseEntity<List<ValidationExceptionDTO>> handleException(BindException ex){
        return ResponseEntity.badRequest().body(
                ex.getAllErrors()
                        .stream()
                        .map(ValidationExceptionDTO::from)
                        .collect(Collectors.toList())
        );
    }

    @ExceptionHandler(RequestException.class)
    public ResponseEntity handleException(RequestException ex, WebRequest request){
        return ResponseEntity.badRequest().body("{\"error\":\"" + ex.getErrorMessage() + "\"}");
    }
}
