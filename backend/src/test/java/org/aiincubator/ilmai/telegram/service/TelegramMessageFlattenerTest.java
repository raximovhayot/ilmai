package org.aiincubator.ilmai.telegram.service;

import org.aiincubator.ilmai.agent.CitationPart;
import org.aiincubator.ilmai.agent.MessagePart;
import org.aiincubator.ilmai.agent.TextConfidence;
import org.aiincubator.ilmai.agent.TextPart;
import org.aiincubator.ilmai.common.i18n.MessageService;
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramMessageFlattenerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private TelegramMessageFlattener flattener;

    @BeforeEach
    void echoLabels() {
        lenient().when(messageService.get(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void escapesHtmlSpecialCharactersInAnswerText() {
        String result = flattener.flatten(List.<MessagePart>of(new TextPart("<b>x</b> & y")), SupportedLocale.EN);

        assertThat(result).contains("&lt;b&gt;x&lt;/b&gt; &amp; y");
        assertThat(result).doesNotContain("<b>x");
    }

    @Test
    void appendsSourcesBlockForCitations() {
        MessagePart text = new TextPart("Photosynthesis converts light to energy.");
        MessagePart citation = new CitationPart(
                UUID.randomUUID(), UUID.randomUUID(), "Biology Notes", "p.1", "leaf chloroplast snippet", 0.91);

        String result = flattener.flatten(List.of(text, citation), SupportedLocale.EN);

        assertThat(result).contains("telegram.bot.flatten.sources");
        assertThat(result).contains("Biology Notes");
        assertThat(result).contains("leaf chloroplast snippet");
    }

    @Test
    void prependsLowConfidenceNoteWhenConfidenceLow() {
        String result = flattener.flatten(
                List.<MessagePart>of(new TextPart("It might be around 1500.", TextConfidence.LOW)), SupportedLocale.EN);

        assertThat(result).contains("telegram.bot.flatten.lowConfidence");
        assertThat(result).contains("It might be around 1500.");
    }

    @Test
    void emptyPartsProduceEmptyString() {
        assertThat(flattener.flatten(List.of(), SupportedLocale.EN)).isEmpty();
    }
}
