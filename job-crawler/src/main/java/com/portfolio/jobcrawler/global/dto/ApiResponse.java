package com.portfolio.jobcrawler.global.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String errorCode;
    private final String message;

    @Builder
    private ApiResponse(boolean success, T data, String errorCode, String message) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.message = message;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }

    public static ApiResponse<Void> ok() {
        return ApiResponse.<Void>builder().success(true).build();
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return ApiResponse.<Void>builder().success(false).errorCode(errorCode).message(message).build();
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> errorTyped(String errorCode, String message) {
        return (ApiResponse<T>) ApiResponse.builder().success(false).errorCode(errorCode).message(message).build();
    }
}
