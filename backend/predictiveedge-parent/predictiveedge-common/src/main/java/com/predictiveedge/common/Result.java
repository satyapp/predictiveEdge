package com.predictiveedge.common;

public record Result<T>(boolean success, T value, String error) {

    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(false, null, error);
    }
}
