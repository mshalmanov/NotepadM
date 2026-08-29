package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Подсветка Markdown: заголовки, код, ссылки, полужирный/курсив, цитаты.
 */
public final class MarkdownSyntaxHighlighter implements SyntaxHighlighter
{
    public static final MarkdownSyntaxHighlighter INSTANCE = new MarkdownSyntaxHighlighter();

    private static final Pattern PATTERN = Pattern.compile(
              "(?<CODEBLOCK>```(.|\\R)*?```)"
            + "|(?<HEADER>^#{1,6}[^\\n]*)"
            + "|(?<BLOCKQUOTE>^>[^\\n]*)"
            + "|(?<CODE>`[^`\\n]+`)"
            + "|(?<LINK>!?\\[[^\\]]*\\]\\([^)]*\\))"
            + "|(?<BOLD>\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__)"
            + "|(?<ITALIC>\\*[^*\\n]+\\*|_[^_\\n]+_)",
            Pattern.MULTILINE
    );

    private MarkdownSyntaxHighlighter() { }

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = PATTERN.matcher(text);
        int lastMatchEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find())
        {
            String styleClass =
                    matcher.group("CODEBLOCK")  != null ? "md-code" :
                    matcher.group("HEADER")     != null ? "md-header" :
                    matcher.group("BLOCKQUOTE") != null ? "md-blockquote" :
                    matcher.group("CODE")       != null ? "md-code" :
                    matcher.group("LINK")       != null ? "md-link" :
                    matcher.group("BOLD")       != null ? "md-bold" :
                    matcher.group("ITALIC")     != null ? "md-italic" :
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
