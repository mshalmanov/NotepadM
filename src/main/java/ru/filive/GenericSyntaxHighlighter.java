package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Регулярочная подсветка для языков с общей структурой токенов: ключевые слова,
 * строки, числа, комментарии, скобки/разделители. Набор ключевых слов и синтаксис
 * комментариев задаётся {@link LanguageSpec} — так один класс покрывает Java, C-подобные,
 * скриптовые и другие языки без дублирования логики токенизации.
 */
public final class GenericSyntaxHighlighter implements SyntaxHighlighter
{
    private static final String STRING_PATTERN =
            "\"([^\"\\\\\\n]|\\\\.)*\"" + "|" + "'([^'\\\\\\n]|\\\\.)*'";
    private static final String NUMBER_PATTERN =
            "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b";
    private static final String PUNCTUATION_PATTERN = "[(){}\\[\\];,:]";

    private final Pattern pattern;

    public GenericSyntaxHighlighter(LanguageSpec spec)
    {
        String keywordPattern = spec.keywords().isEmpty()
                ? "(?!)" // никогда не совпадает
                : "\\b(" + String.join("|", spec.keywords()) + ")\\b";

        StringBuilder commentPattern = new StringBuilder();
        for (String lineComment : spec.lineComments())
        {
            if (commentPattern.length() > 0)
            {
                commentPattern.append("|");
            }
            commentPattern.append(Pattern.quote(lineComment)).append("[^\\n]*");
        }
        if (spec.blockCommentStart() != null && spec.blockCommentEnd() != null)
        {
            if (commentPattern.length() > 0)
            {
                commentPattern.append("|");
            }
            commentPattern.append(Pattern.quote(spec.blockCommentStart()))
                    .append("(.|\\R)*?")
                    .append(Pattern.quote(spec.blockCommentEnd()));
        }
        String commentGroup = commentPattern.length() > 0 ? commentPattern.toString() : "(?!)";

        String regex =
                  "(?<COMMENT>" + commentGroup + ")"
                + "|(?<STRING>" + STRING_PATTERN + ")"
                + "|(?<KEYWORD>" + keywordPattern + ")"
                + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                + "|(?<PUNCTUATION>" + PUNCTUATION_PATTERN + ")";

        this.pattern = spec.caseInsensitiveKeywords()
                ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                : Pattern.compile(regex);
    }

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = pattern.matcher(text);
        int lastMatchEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find())
        {
            String styleClass =
                    matcher.group("COMMENT")     != null ? "comment" :
                    matcher.group("STRING")      != null ? "string" :
                    matcher.group("KEYWORD")     != null ? "keyword" :
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
