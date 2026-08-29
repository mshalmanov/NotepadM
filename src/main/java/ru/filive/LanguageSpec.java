package ru.filive;

import java.util.List;

/**
 * Описание синтаксиса языка для {@link GenericSyntaxHighlighter}:
 * ключевые слова и правила однострочных/блочных комментариев.
 */
public record LanguageSpec(
        List<String> keywords,
        List<String> lineComments,
        String blockCommentStart,
        String blockCommentEnd,
        boolean caseInsensitiveKeywords)
{
    public LanguageSpec(List<String> keywords, List<String> lineComments)
    {
        this(keywords, lineComments, null, null, false);
    }

    public LanguageSpec(List<String> keywords, List<String> lineComments,
                         String blockCommentStart, String blockCommentEnd)
    {
        this(keywords, lineComments, blockCommentStart, blockCommentEnd, false);
    }
}
