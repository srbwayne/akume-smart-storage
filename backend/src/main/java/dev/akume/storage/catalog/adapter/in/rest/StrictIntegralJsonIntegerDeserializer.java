package dev.akume.storage.catalog.adapter.in.rest;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.DeserializationContext;

import java.math.BigInteger;

/** Accepts only integral JSON number tokens within Java Integer range. */
final class StrictIntegralJsonIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(this, "expectedVersion must be an integral JSON number");
        }
        BigInteger value = parser.getBigIntegerValue();
        if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            return context.reportInputMismatch(this, "expectedVersion is outside the supported integer range");
        }
        return value.intValue();
    }
}
