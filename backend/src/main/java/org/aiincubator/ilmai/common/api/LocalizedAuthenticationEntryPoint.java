package org.aiincubator.ilmai.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.payload.ApiError;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LocalizedAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final MessageService messages;
    private final LocaleResolver localeResolver;
    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String localized = messages.get("error.unauthorized", null, localeResolver.resolveLocale(request));
        ApiResponse<Void> body = ApiResponse.fail(ApiError.of("UNAUTHORIZED", localized));
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
