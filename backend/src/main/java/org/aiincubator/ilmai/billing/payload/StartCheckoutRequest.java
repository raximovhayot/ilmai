package org.aiincubator.ilmai.billing.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StartCheckoutRequest {

    @NotBlank
    @Size(max = 20)
    private String plan;

    @NotBlank
    @Size(max = 20)
    private String provider;
}
