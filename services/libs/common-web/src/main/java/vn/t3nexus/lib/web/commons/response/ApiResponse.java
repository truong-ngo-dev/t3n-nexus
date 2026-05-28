package vn.t3nexus.lib.web.commons.response;

import java.util.List;

/**
 * Standard envelope for all API responses.
 *
 * @param success indicates if the operation was successful
 * @param data    the actual response payload (only for successful requests)
 * @param message a human-readable message, typically used for errors
 * @param errors  a list of detailed error messages (e.g., validation failures)
 * @param <T>     the type of the response data
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<String> errors
) {
    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, List.of());
    }

    /**
     * Creates an error response with a message.
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message, List.of());
    }

    /**
     * Creates a validation error response with detailed messages.
     */
    public static <T> ApiResponse<T> validationError(List<String> errors) {
        return new ApiResponse<>(false, null, "Validation failed", errors);
    }
}
