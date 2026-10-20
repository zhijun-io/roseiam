package io.roseiam.config;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class JsonCodec {

    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not encode JSON value", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> readMap(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not decode stored JSON value", exception);
        }
    }
}
