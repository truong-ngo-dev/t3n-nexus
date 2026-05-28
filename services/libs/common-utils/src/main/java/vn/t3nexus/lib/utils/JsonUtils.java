package vn.t3nexus.lib.utils;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Json utility
 * @author Truong Ngo
 * @version 1.0.0
 * */
public class JsonUtils {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();


    /**
     * Converts a JSON string into an object of the specified type {@code T}.
     *
     * @param json  The JSON string to be converted.
     * @param clazz The class type of {@code T}.
     * @param <T>   The target object type.
     * @return An object of type {@code T} parsed from the JSON string, or {@code null} if the input is {@code null}.
     * @throws IllegalArgumentException If the JSON string is invalid or cannot be deserialized.
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return Objects.isNull(json) ? null : MAPPER.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse json string: " + json, e);
        }
    }


    /**
     * Converts a JSON string into an object of the specified parameterized type {@code T}.
     *
     * @param json The JSON string to be converted.
     * @param type The parameterized type reference of {@code T}.
     * @param <T>  The target object type.
     * @return An object of type {@code T} parsed from the JSON string, or {@code null} if the input is {@code null}.
     * @throws IllegalArgumentException If the JSON string is invalid or cannot be deserialized.
     */
    public static <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return Objects.isNull(json) ? null : MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse json string: " + json, e);
        }
    }


    /**
     * Serializes an object into a JSON string.
     *
     * @param obj The object to be serialized.
     * @return A JSON string representation of the object.
     * @throws IllegalArgumentException If serialization fails.
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON", e);
        }
    }
}
