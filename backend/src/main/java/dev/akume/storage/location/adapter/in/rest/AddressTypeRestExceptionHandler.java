package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeInUseException;
import dev.akume.storage.location.domain.exception.AddressTypeCodeAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AddressTypeRestController.class)
public class AddressTypeRestExceptionHandler {

    @ExceptionHandler(AddressTypeNotFoundException.class)
    public ResponseEntity<RestErrorResponse> notFound(AddressTypeNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ADDRESS_TYPE_NOT_FOUND", "Address type was not found", request);
    }

    @ExceptionHandler(AddressTypeCodeAlreadyExistsException.class)
    public ResponseEntity<RestErrorResponse> duplicateCode(
            AddressTypeCodeAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_TYPE_CODE_ALREADY_EXISTS", "Address type code already exists", request);
    }

    @ExceptionHandler(AddressTypeInUseException.class)
    public ResponseEntity<RestErrorResponse> inUse(AddressTypeInUseException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_TYPE_IN_USE",
                "Address type is used by active addresses", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RestErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is malformed or contains an unknown property", request);
    }

    private ResponseEntity<RestErrorResponse> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new RestErrorResponse(status.value(), code, message, request.getRequestURI()));
    }
}
