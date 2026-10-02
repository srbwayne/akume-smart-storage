package dev.akume.storage.location.adapter.in.rest;

/** Stable, deliberately small error contract for AddressType HTTP requests. */
public record RestErrorResponse(int status, String code, String message, String path) {
}
