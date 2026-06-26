package org.aiincubator.ilmai.auth.payload;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DevLoginRequest {

    @Email
    private String email;

    private String name;
}
