package com.jynx.pro.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jynx.pro.exception.JynxProException;
import org.springframework.stereotype.Component;

@Component
public class JSONUtils {

    private final ObjectMapper objectMapper;

    /**
     * Construct Spring bean and configure {@link ObjectMapper}
     *
     * @param objectMapper the {@link ObjectMapper} bean
     */
    public JSONUtils(
            final ObjectMapper objectMapper
    ) {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.objectMapper = objectMapper;
    }

    /**
     * Converts object to JSON string
     *
     * @param object object to convert
     *
     * @return JSON string
     */
    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JynxProException(e.getMessage());
        }
    }

    /**
     * Converts JSON string to object
     *
     * @param json the JSON string
     * @param type the target object type
     *
     * @param <T> generic type
     *
     * @return the object
     */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new JynxProException(e.getMessage());
        }
    }
}