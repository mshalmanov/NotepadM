package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Подсветка YAML: комментарии, строки, числа, true/false/null, а также ключи
 * (слово перед двоеточием) — без этого простые файлы вида "key: value"
 * оставались бы почти без подсветки.
 */
public final class YamlSyntaxHighlighter implements SyntaxHighlighter
{
    public static final YamlSyntaxHighlighter INSTANCE = new YamlSyntaxHighlighter();

    private static final Pattern PATTERN = Pattern.compile(
              "(?<COMMENT>#[^\\n]*)"
            + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*')"
            + "|(?<KEYWORD>\\b(true|false|null|yes|no|on|off)\\b)"
            + "|(?<NUMBER>-?\\d+(\\.\\d+)?\\b)"
            + "|(?<KEY>[\\w.-]+(?=\\h*:(\\h|$)))"
            + "|(?<DASH>^\\h*-(?=\\h))",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private YamlSyntaxHighlighter() { }

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = PATTERN.matcher(text);
        int lastMatchEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find())
        {
            String styleClass =
                    matcher.group("COMMENT") != null ? "comment" :
                    matcher.group("STRING")  != null ? "string" :
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("NUMBER")  != null ? "number" :
                    matcher.group("KEY")     != null ? "property" :
                    matcher.group("DASH")    != null ? "punctuation" :
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
