package com.storemanager.purchasing.infrastructure.web;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

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
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Integer) context.handleUnexpectedToken(Integer.class, parser);
        }
        return parser.getIntValue();
    }
}
