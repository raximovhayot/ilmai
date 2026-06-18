package org.aiincubator.ilmai.telegram.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMarkdownRendererTest {

    private final TelegramMarkdownRenderer renderer = new TelegramMarkdownRenderer();

    @Test
    void escapesAllMarkdownV2SpecialCharacters() {
        assertThat(renderer.escape("a.b-c!(d)")).isEqualTo("a\\.b\\-c\\!\\(d\\)");
    }

    @Test
    void convertsBoldItalicAndStrike() {
        assertThat(renderer.render("**b** __b2__ *i* _i2_ ~~s~~"))
                .isEqualTo("*b* *b2* _i_ _i2_ ~s~");
    }

    @Test
    void convertsHeadingsToBold() {
        assertThat(renderer.render("# Heading")).isEqualTo("*Heading*");
        assertThat(renderer.render("### Sub")).isEqualTo("*Sub*");
    }

    @Test
    void convertsBulletListToBulletCharacter() {
        assertThat(renderer.render("- one\n* two\n+ three"))
                .isEqualTo("\u2022 one\n\u2022 two\n\u2022 three");
    }

    @Test
    void escapesLiteralTextOutsideEntities() {
        assertThat(renderer.render("Cost is 5.0 (USD)!")).isEqualTo("Cost is 5\\.0 \\(USD\\)\\!");
    }

    @Test
    void rendersInlineAndFencedCodeWithEscaping() {
        String out = renderer.render("use `a.b` then\n```\nx.y\n```");

        assertThat(out).contains("`a.b`");
        assertThat(out).contains("```\nx.y\n```");
    }

    @Test
    void doesNotInterpretMarkdownInsideCode() {
        assertThat(renderer.render("`**not bold**`")).isEqualTo("`**not bold**`");
    }

    @Test
    void rendersLinksWithEscapedText() {
        assertThat(renderer.render("see [the docs.](https://example.com/x)"))
                .isEqualTo("see [the docs\\.](https://example.com/x)");
    }

    @Test
    void leavesSnakeCaseUntouchedExceptForRequiredEscaping() {
        assertThat(renderer.render("call some method now")).isEqualTo("call some method now");
    }
}
