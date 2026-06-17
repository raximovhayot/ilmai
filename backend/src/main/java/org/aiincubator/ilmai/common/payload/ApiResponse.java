package org.aiincubator.ilmai.common.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private T data;
    private List<ApiError> errors;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> fail(List<ApiError> errors) {
        return new ApiResponse<>(null, errors);
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(null, List.of(error));
    }
}
