package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.exception.InactiveItemCategoryException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import dev.akume.storage.catalog.application.exception.ItemConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ItemRestController.class)
public class ItemRestExceptionHandler {

    @ExceptionHandler(ItemNotFoundException.class)
    public ResponseEntity<RestErrorResponse> itemNotFound(ItemNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Item was not found.", request);
    }

    @ExceptionHandler(ItemCategoryNotFoundException.class)
    public ResponseEntity<RestErrorResponse> categoryNotFound(ItemCategoryNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ITEM_CATEGORY_NOT_FOUND", "Item category was not found.", request);
    }

    @ExceptionHandler(InactiveItemCategoryException.class)
    public ResponseEntity<RestErrorResponse> inactiveCategory(InactiveItemCategoryException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ITEM_CATEGORY_INACTIVE",
                "An inactive item category cannot receive a new Item assignment.", request);
    }

    @ExceptionHandler(ItemConcurrentModificationException.class)
    public ResponseEntity<RestErrorResponse> concurrentModification(ItemConcurrentModificationException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ITEM_CONCURRENT_MODIFICATION",
                "Item was changed concurrently. Reload and retry.", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<RestErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed.", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RestErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is malformed or contains an unknown property.", request);
    }

    private ResponseEntity<RestErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new RestErrorResponse(status.value(), code, message, request.getRequestURI()));
    }
}
