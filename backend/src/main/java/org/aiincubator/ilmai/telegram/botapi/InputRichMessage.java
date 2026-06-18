package org.aiincubator.ilmai.telegram.botapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.telegram.telegrambots.meta.api.interfaces.BotApiObject;
import org.telegram.telegrambots.meta.api.interfaces.Validable;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;

@EqualsAndHashCode(callSuper = false)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InputRichMessage implements BotApiObject, Validable {

    private static final String HTML_FIELD = "html";
    private static final String MARKDOWN_FIELD = "markdown";
    private static final String IS_RTL_FIELD = "is_rtl";
    private static final String SKIP_ENTITY_DETECTION_FIELD = "skip_entity_detection";

    @JsonProperty(HTML_FIELD)
    private String html;

    @JsonProperty(MARKDOWN_FIELD)
    private String markdown;

    @JsonProperty(IS_RTL_FIELD)
    private Boolean isRtl;

    @JsonProperty(SKIP_ENTITY_DETECTION_FIELD)
    private Boolean skipEntityDetection;

    @Override
    public void validate() throws TelegramApiValidationException {
        boolean hasHtml = html != null && !html.isEmpty();
        boolean hasMarkdown = markdown != null && !markdown.isEmpty();
        if (hasHtml == hasMarkdown) {
            throw new TelegramApiValidationException("Exactly one of html or markdown must be used", this);
        }
    }
}
