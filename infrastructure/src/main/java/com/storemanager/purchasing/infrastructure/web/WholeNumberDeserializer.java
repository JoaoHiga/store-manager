package com.storemanager.purchasing.infrastructure.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Accepts only a JSON integer that fits an {@code int}. Jackson's default would truncate {@code 1.5} to
 * {@code 1} and silently record fewer units than were sent; here a fraction, a string or an overflow is a
 * malformed request (400).
 */
final class WholeNumberDeserializer extends StdDeserializer<Integer> {

    WholeNumberDeserializer() {
        super(Integer.class);
    }

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        return parser.getIntValue();
    }
}
