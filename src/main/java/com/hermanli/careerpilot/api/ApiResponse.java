package com.hermanli.careerpilot.api;

public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
