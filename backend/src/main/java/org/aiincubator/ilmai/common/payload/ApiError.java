package org.aiincubator.ilmai.common.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private String field;
    private String code;
    private String message;

    public static ApiError of(String code, String message) {
        return new ApiError(null, code, message);
    }

    public static ApiError of(String field, String code, String message) {
        return new ApiError(field, code, message);
    }
}
