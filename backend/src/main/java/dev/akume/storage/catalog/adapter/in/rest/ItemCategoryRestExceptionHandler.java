package dev.akume.storage.catalog.adapter.in.rest;

import dev.akume.storage.catalog.application.exception.ItemCategoryConcurrentModificationException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNameAlreadyExistsException;
import dev.akume.storage.catalog.application.exception.ItemCategoryNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ItemCategoryRestController.class)
public class ItemCategoryRestExceptionHandler {

    @ExceptionHandler(ItemCategoryNotFoundException.class)
    public ResponseEntity<RestErrorResponse> notFound(
            ItemCategoryNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "ITEM_CATEGORY_NOT_FOUND",
                "Item category was not found.", request);
    }

    @ExceptionHandler(ItemCategoryNameAlreadyExistsException.class)
    public ResponseEntity<RestErrorResponse> duplicateName(
            ItemCategoryNameAlreadyExistsException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ITEM_CATEGORY_NAME_ALREADY_EXISTS",
                "An item category with this name already exists.", request);
    }

    @ExceptionHandler(ItemCategoryConcurrentModificationException.class)
    public ResponseEntity<RestErrorResponse> concurrentModification(
            ItemCategoryConcurrentModificationException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "ITEM_CATEGORY_CONCURRENT_MODIFICATION",
                "Item category was changed concurrently. Reload and retry.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed.", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RestErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is malformed or contains an unknown property.", request);
    }

    private ResponseEntity<RestErrorResponse> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new RestErrorResponse(status.value(), code, message, request.getRequestURI()));
    }
}
