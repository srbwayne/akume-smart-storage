package dev.akume.storage.location.adapter.in.rest;

import dev.akume.storage.location.application.exception.AddressAlreadyExistsException;
import dev.akume.storage.location.application.exception.AddressConcurrentModificationException;
import dev.akume.storage.location.application.exception.AddressCycleDetectedException;
import dev.akume.storage.location.application.exception.AddressHasActiveChildrenException;
import dev.akume.storage.location.application.exception.AddressNotFoundException;
import dev.akume.storage.location.application.exception.AddressTypeNotFoundException;
import dev.akume.storage.location.application.exception.InactiveAddressParentException;
import dev.akume.storage.location.application.exception.InactiveAddressTypeException;
import dev.akume.storage.location.application.exception.SerializableOperationConflictException;
import dev.akume.storage.location.domain.exception.AddressSiblingNameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AddressRestController.class)
public class AddressRestExceptionHandler {
    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<RestErrorResponse> addressNotFound(AddressNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Address was not found.", request);
    }

    @ExceptionHandler(AddressTypeNotFoundException.class)
    public ResponseEntity<RestErrorResponse> addressTypeNotFound(AddressTypeNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ADDRESS_TYPE_NOT_FOUND", "Address type was not found.", request);
    }

    @ExceptionHandler(InactiveAddressTypeException.class)
    public ResponseEntity<RestErrorResponse> inactiveType(InactiveAddressTypeException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_TYPE_INACTIVE", "Address type is inactive.", request);
    }

    @ExceptionHandler(InactiveAddressParentException.class)
    public ResponseEntity<RestErrorResponse> inactiveParent(InactiveAddressParentException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_PARENT_INACTIVE", "An active Address requires an active parent.", request);
    }

    @ExceptionHandler(AddressSiblingNameAlreadyExistsException.class)
    public ResponseEntity<RestErrorResponse> siblingName(AddressSiblingNameAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_SIBLING_NAME_ALREADY_EXISTS",
                "An Address with this sibling name already exists.", request);
    }

    @ExceptionHandler(AddressCycleDetectedException.class)
    public ResponseEntity<RestErrorResponse> cycle(AddressCycleDetectedException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_CYCLE_DETECTED", "The move would create a hierarchy cycle.", request);
    }

    @ExceptionHandler(AddressHasActiveChildrenException.class)
    public ResponseEntity<RestErrorResponse> activeChildren(AddressHasActiveChildrenException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_HAS_ACTIVE_CHILDREN", "Address has active direct children.", request);
    }

    @ExceptionHandler({AddressConcurrentModificationException.class, SerializableOperationConflictException.class})
    public ResponseEntity<RestErrorResponse> concurrentModification(RuntimeException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_CONCURRENT_MODIFICATION",
                "Address was changed concurrently. Reload and retry.", request);
    }

    @ExceptionHandler(AddressAlreadyExistsException.class)
    public ResponseEntity<RestErrorResponse> alreadyExists(AddressAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ADDRESS_ALREADY_EXISTS", "Address already exists.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> invalidRequest(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed.", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RestErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is malformed or contains an unknown property.", request);
    }

    private ResponseEntity<RestErrorResponse> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new RestErrorResponse(status.value(), code, message, request.getRequestURI()));
    }
}
