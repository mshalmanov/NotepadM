package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Подсветка тегов/атрибутов для HTML и XML.
 */
public final class MarkupSyntaxHighlighter implements SyntaxHighlighter
{
    public static final MarkupSyntaxHighlighter INSTANCE = new MarkupSyntaxHighlighter();

    private static final Pattern TAG_PATTERN = Pattern.compile(
              "(?<ELEMENT>(</?\\h*)([\\w:.-]+)([^<>]*)(\\h*/?>))"
            + "|(?<COMMENT><!--[^<>]*-->)"
            + "|(?<DOCTYPE><!(?i:DOCTYPE)[^<>]*>)");

    private static final Pattern ATTRIBUTES_PATTERN = Pattern.compile(
            "([\\w:-]+)(\\h*=\\h*)(\"[^\"]*\"|'[^']*')");

    private static final int GROUP_OPEN_BRACKET = 2;
    private static final int GROUP_ELEMENT_NAME = 3;
    private static final int GROUP_ATTRIBUTES_SECTION = 4;
    private static final int GROUP_CLOSE_BRACKET = 5;

    private static final int ATTR_GROUP_NAME = 1;
    private static final int ATTR_GROUP_EQUALS = 2;
    private static final int ATTR_GROUP_VALUE = 3;

    private MarkupSyntaxHighlighter() { }

    @Override
    public StyleSpans<Collection<String>> computeHighlighting(String text)
    {
        Matcher matcher = TAG_PATTERN.matcher(text);
        int lastMatchEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find())
        {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastMatchEnd);

            if (matcher.group("COMMENT") != null || matcher.group("DOCTYPE") != null)
            {
                spansBuilder.add(Collections.singleton("comment"), matcher.end() - matcher.start());
            }
            else if (matcher.group("ELEMENT") != null)
            {
                String attributesText = matcher.group(GROUP_ATTRIBUTES_SECTION);

                spansBuilder.add(Collections.singleton("tagmark"),
                        matcher.end(GROUP_OPEN_BRACKET) - matcher.start(GROUP_OPEN_BRACKET));
                spansBuilder.add(Collections.singleton("anytag"),
                        matcher.end(GROUP_ELEMENT_NAME) - matcher.end(GROUP_OPEN_BRACKET));

                if (!attributesText.isEmpty())
                {
                    int attrLastEnd = 0;
                    Matcher attrMatcher = ATTRIBUTES_PATTERN.matcher(attributesText);
                    while (attrMatcher.find())
                    {
                        spansBuilder.add(Collections.emptyList(), attrMatcher.start() - attrLastEnd);
                        spansBuilder.add(Collections.singleton("attribute"),
                                attrMatcher.end(ATTR_GROUP_NAME) - attrMatcher.start(ATTR_GROUP_NAME));
                        spansBuilder.add(Collections.singleton("tagmark"),
                                attrMatcher.end(ATTR_GROUP_EQUALS) - attrMatcher.end(ATTR_GROUP_NAME));
                        spansBuilder.add(Collections.singleton("avalue"),
                                attrMatcher.end(ATTR_GROUP_VALUE) - attrMatcher.end(ATTR_GROUP_EQUALS));
                        attrLastEnd = attrMatcher.end();
                    }
                    if (attributesText.length() > attrLastEnd)
                    {
                        spansBuilder.add(Collections.emptyList(), attributesText.length() - attrLastEnd);
                    }
                }

                spansBuilder.add(Collections.singleton("tagmark"),
                        matcher.end(GROUP_CLOSE_BRACKET) - matcher.end(GROUP_ATTRIBUTES_SECTION));
            }

            lastMatchEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastMatchEnd);
        return spansBuilder.create();
    }
}
