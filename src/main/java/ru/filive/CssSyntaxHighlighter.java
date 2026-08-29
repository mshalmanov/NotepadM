package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Подсветка CSS: комментарии, строки, hex-цвета, @-правила, свойства, числа.
 */
public final class CssSyntaxHighlighter implements SyntaxHighlighter
{
    public static final CssSyntaxHighlighter INSTANCE = new CssSyntaxHighlighter();

    private static final Pattern PATTERN = Pattern.compile(
              "(?<COMMENT>/\\*(.|\\R)*?\\*/)"
            + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"
            + "|(?<HEXCOLOR>#[0-9a-fA-F]{3,8}\\b)"
            + "|(?<ATRULE>@[a-zA-Z-]+)"
            + "|(?<PROPERTY>[a-zA-Z-]+(?=\\h*:))"
            + "|(?<NUMBER>-?\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|pt|s|ms|deg)?)"
            + "|(?<PUNCTUATION>[{};:,])"
    );

    private CssSyntaxHighlighter() { }

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = PATTERN.matcher(text);
        int lastMatchEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find())
        {
            String styleClass =
                    matcher.group("COMMENT")     != null ? "comment" :
                    matcher.group("STRING")      != null ? "string" :
                    matcher.group("HEXCOLOR")    != null ? "hex-color" :
                    matcher.group("ATRULE")      != null ? "at-rule" :
                    matcher.group("PROPERTY")    != null ? "property" :
                    matcher.group("NUMBER")      != null ? "number" :
                    matcher.group("PUNCTUATION") != null ? "punctuation" :
                    null;
            if (styleClass == null)
            {
                continue;
            }
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastMatchEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastMatchEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastMatchEnd);
        return spansBuilder.create();
    }
}
