package ru.filive;

import java.util.Collection;

import org.fxmisc.richtext.model.StyleSpans;

@FunctionalInterface
public interface SyntaxHighlighter
{
    StyleSpans<Collection<String>> computeHighlighting(String text);
}
