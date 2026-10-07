package dev.akume.storage.catalog.adapter.in.rest;

/** Stable, deliberately small error contract for ItemCategory HTTP requests. */
public record RestErrorResponse(int status, String code, String message, String path) {
}
