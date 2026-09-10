package ru.filive;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Сопоставляет расширение файла с подсветкой синтаксиса.
 */
public final class SyntaxHighlighters
{
    /** Без подсветки — используется для обычного текста и неизвестных расширений. */
    public static final SyntaxHighlighter PLAIN = text -> {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        builder.add(Collections.emptyList(), text.length());
        return builder.create();
    };

    private static final List<String> C_LINE = List.of("//");
    private static final String C_BLOCK_START = "/*";
    private static final String C_BLOCK_END = "*/";

    private static final SyntaxHighlighter JAVA = generic(List.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "record", "return", "sealed", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while",
            "yield", "true", "false", "null", "var"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter C_CPP = generic(List.of(
            "auto", "break", "case", "char", "const", "continue", "default",
            "do", "double", "else", "enum", "extern", "float", "for", "goto",
            "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch",
            "typedef", "union", "unsigned", "void", "volatile", "while",
            "class", "namespace", "public", "private", "protected", "friend",
            "virtual", "template", "typename", "using", "new", "delete",
            "try", "catch", "throw", "this", "true", "false", "nullptr",
            "constexpr", "explicit", "operator", "override", "final"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter CSHARP = generic(List.of(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch",
            "char", "checked", "class", "const", "continue", "decimal",
            "default", "delegate", "do", "double", "else", "enum", "event",
            "explicit", "extern", "false", "finally", "fixed", "float", "for",
            "foreach", "goto", "if", "implicit", "in", "int", "interface",
            "internal", "is", "lock", "long", "namespace", "new", "null",
            "object", "operator", "out", "override", "params", "private",
            "protected", "public", "readonly", "record", "ref", "return",
            "sbyte", "sealed", "short", "sizeof", "stackalloc", "static",
            "string", "struct", "switch", "this", "throw", "true", "try",
            "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort",
            "using", "var", "virtual", "void", "volatile", "while", "async", "await"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final List<String> JS_KEYWORDS = List.of(
            "async", "await", "break", "case", "catch", "class", "const",
            "continue", "debugger", "default", "delete", "do", "else",
            "export", "extends", "finally", "for", "function", "if", "import",
            "in", "instanceof", "let", "new", "null", "of", "return", "static",
            "super", "switch", "this", "throw", "true", "false", "try",
            "typeof", "undefined", "var", "void", "while", "with", "yield"
    );
    private static final SyntaxHighlighter JAVASCRIPT = generic(JS_KEYWORDS, C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter TYPESCRIPT = generic(concat(JS_KEYWORDS, List.of(
            "interface", "type", "enum", "implements", "private", "protected",
            "public", "readonly", "namespace", "declare", "abstract", "as", "is"
    )), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter PYTHON = generic(List.of(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else",
            "except", "finally", "for", "from", "global", "if", "import",
            "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield"
    ), List.of("#"));

    private static final SyntaxHighlighter GO = generic(List.of(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if",
            "import", "interface", "map", "package", "range", "return",
            "select", "struct", "switch", "type", "var", "true", "false", "nil"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter RUST = generic(List.of(
            "as", "break", "const", "continue", "crate", "dyn", "else", "enum",
            "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self",
            "Self", "static", "struct", "super", "trait", "true", "type",
            "unsafe", "use", "where", "while", "async", "await"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter KOTLIN = generic(List.of(
            "as", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true", "try", "typealias",
            "val", "var", "when", "while", "by", "companion", "constructor",
            "data", "enum", "import", "init", "internal", "override",
            "private", "protected", "public", "sealed", "suspend"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter SWIFT = generic(List.of(
            "associatedtype", "class", "deinit", "enum", "extension", "func",
            "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "protocol", "public", "rethrows", "static", "struct",
            "subscript", "typealias", "var", "break", "case", "continue",
            "default", "defer", "do", "else", "fallthrough", "for", "guard",
            "if", "in", "repeat", "return", "switch", "where", "while", "as",
            "Any", "catch", "false", "is", "nil", "self", "Self", "super",
            "throw", "throws", "true", "try"
    ), C_LINE, C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter PHP = generic(List.of(
            "abstract", "and", "array", "as", "break", "callable", "case",
            "catch", "class", "clone", "const", "continue", "declare",
            "default", "do", "echo", "else", "elseif", "empty", "extends",
            "final", "finally", "fn", "for", "foreach", "function", "global",
            "goto", "if", "implements", "include", "include_once",
            "instanceof", "insteadof", "interface", "isset", "list", "match",
            "namespace", "new", "or", "print", "private", "protected",
            "public", "require", "require_once", "return", "static", "switch",
            "throw", "trait", "try", "unset", "use", "var", "while", "xor",
            "yield", "true", "false", "null"
    ), List.of("//", "#"), C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter RUBY = generic(List.of(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class",
            "def", "defined?", "do", "else", "elsif", "end", "ensure",
            "false", "for", "if", "in", "module", "next", "nil", "not", "or",
            "redo", "rescue", "retry", "return", "self", "super", "then",
            "true", "undef", "unless", "until", "when", "while", "yield"
    ), List.of("#"));

    private static final SyntaxHighlighter SQL = genericCI(List.of(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "JOIN",
            "INNER", "LEFT", "RIGHT", "OUTER", "ON", "AS", "AND", "OR", "NOT",
            "NULL", "IS", "IN", "LIKE", "GROUP", "BY", "ORDER", "HAVING",
            "LIMIT", "DISTINCT", "UNION", "ALL", "EXISTS", "BETWEEN", "CASE",
            "WHEN", "THEN", "ELSE", "END", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "DEFAULT", "INDEX", "VIEW", "TRIGGER", "PROCEDURE",
            "FUNCTION", "DECLARE", "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION"
    ), List.of("--"), C_BLOCK_START, C_BLOCK_END);

    private static final SyntaxHighlighter JSON = generic(
            List.of("true", "false", "null"), List.of());

    private static final SyntaxHighlighter SHELL = generic(List.of(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do",
            "done", "case", "esac", "function", "in", "return", "break",
            "continue", "local", "export", "readonly", "shift", "exit",
            "echo", "true", "false"
    ), List.of("#"));

    private static final SyntaxHighlighter POWERSHELL = genericCI(List.of(
            "begin", "break", "catch", "class", "continue", "data", "do",
            "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter",
            "finally", "for", "foreach", "from", "function", "if", "in",
            "param", "process", "return", "switch", "throw", "trap", "try",
            "until", "using", "var", "while", "true", "false", "null"
    ), List.of("#"), "<#", "#>");

    private static final SyntaxHighlighter PERL = generic(List.of(
            "my", "our", "local", "sub", "if", "elsif", "else", "unless",
            "while", "until", "for", "foreach", "do", "last", "next", "redo",
            "return", "package", "use", "require", "and", "or", "not", "eq",
            "ne", "lt", "gt", "le", "ge", "cmp", "print", "defined", "undef",
            "ref", "bless", "die", "warn"
    ), List.of("#"));

    private static final SyntaxHighlighter LUA = generic(List.of(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or",
            "repeat", "return", "then", "true", "until", "while"
    ), List.of("--"), "--[[", "]]");

    private static final SyntaxHighlighter R_LANG = generic(List.of(
            "if", "else", "repeat", "while", "function", "for", "next",
            "break", "TRUE", "FALSE", "NULL", "Inf", "NaN", "NA", "in"
    ), List.of("#"));

    private static final SyntaxHighlighter INI = generic(List.of(), List.of(";", "#"));

    private static final SyntaxHighlighter BATCH = genericCI(List.of(
            "echo", "set", "if", "else", "for", "goto", "call", "exit",
            "pause", "rem", "setlocal", "endlocal", "shift", "cls"
    ), List.of("REM", "::"));

    /** Подсветка + отображаемое имя языка (для строки состояния) на одно расширение файла. */
    private record Language(SyntaxHighlighter highlighter, String displayName) { }

    private static final Language PLAIN_LANGUAGE = new Language(PLAIN, "Plain Text");

    private static final Map<String, Language> LANGUAGES = buildMap();

    private SyntaxHighlighters() { }

    public static SyntaxHighlighter forFileName(String fileName)
    {
        return languageFor(fileName).highlighter();
    }

    /** Отображаемое имя языка для строки состояния (например, "Java", "Plain Text"). */
    public static String nameForFileName(String fileName)
    {
        return languageFor(fileName).displayName();
    }

    private static Language languageFor(String fileName)
    {
        if (fileName == null)
        {
            return PLAIN_LANGUAGE;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1)
        {
            return PLAIN_LANGUAGE;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return LANGUAGES.getOrDefault(extension, PLAIN_LANGUAGE);
    }

    private static Map<String, Language> buildMap()
    {
        Map<String, Language> map = new HashMap<>();
        map.put("java", new Language(JAVA, "Java"));
        map.put("c", new Language(C_CPP, "C"));
        map.put("h", new Language(C_CPP, "C"));
        map.put("cpp", new Language(C_CPP, "C++"));
        map.put("cc", new Language(C_CPP, "C++"));
        map.put("cxx", new Language(C_CPP, "C++"));
        map.put("hpp", new Language(C_CPP, "C++"));
        map.put("hh", new Language(C_CPP, "C++"));
        map.put("hxx", new Language(C_CPP, "C++"));
        map.put("cs", new Language(CSHARP, "C#"));
        map.put("js", new Language(JAVASCRIPT, "JavaScript"));
        map.put("mjs", new Language(JAVASCRIPT, "JavaScript"));
        map.put("cjs", new Language(JAVASCRIPT, "JavaScript"));
        map.put("jsx", new Language(JAVASCRIPT, "JavaScript"));
        map.put("ts", new Language(TYPESCRIPT, "TypeScript"));
        map.put("tsx", new Language(TYPESCRIPT, "TypeScript"));
        map.put("py", new Language(PYTHON, "Python"));
        map.put("pyw", new Language(PYTHON, "Python"));
        map.put("go", new Language(GO, "Go"));
        map.put("rs", new Language(RUST, "Rust"));
        map.put("kt", new Language(KOTLIN, "Kotlin"));
        map.put("kts", new Language(KOTLIN, "Kotlin"));
        map.put("swift", new Language(SWIFT, "Swift"));
        map.put("php", new Language(PHP, "PHP"));
        map.put("rb", new Language(RUBY, "Ruby"));
        map.put("sql", new Language(SQL, "SQL"));
        map.put("json", new Language(JSON, "JSON"));
        map.put("yml", new Language(YamlSyntaxHighlighter.INSTANCE, "YAML"));
        map.put("yaml", new Language(YamlSyntaxHighlighter.INSTANCE, "YAML"));
        map.put("sh", new Language(SHELL, "Shell Script"));
        map.put("bash", new Language(SHELL, "Shell Script"));
        map.put("zsh", new Language(SHELL, "Shell Script"));
        map.put("ps1", new Language(POWERSHELL, "PowerShell"));
        map.put("md", new Language(MarkdownSyntaxHighlighter.INSTANCE, "Markdown"));
        map.put("markdown", new Language(MarkdownSyntaxHighlighter.INSTANCE, "Markdown"));
        map.put("html", new Language(MarkupSyntaxHighlighter.INSTANCE, "HTML"));
        map.put("htm", new Language(MarkupSyntaxHighlighter.INSTANCE, "HTML"));
        map.put("xhtml", new Language(MarkupSyntaxHighlighter.INSTANCE, "HTML"));
        map.put("xml", new Language(MarkupSyntaxHighlighter.INSTANCE, "XML"));
        map.put("css", new Language(CssSyntaxHighlighter.INSTANCE, "CSS"));
        map.put("pl", new Language(PERL, "Perl"));
        map.put("pm", new Language(PERL, "Perl"));
        map.put("lua", new Language(LUA, "Lua"));
        map.put("r", new Language(R_LANG, "R"));
        map.put("ini", new Language(INI, "INI"));
        map.put("cfg", new Language(INI, "INI"));
        map.put("properties", new Language(INI, "INI"));
        map.put("conf", new Language(INI, "INI"));
        map.put("bat", new Language(BATCH, "Batch"));
        map.put("cmd", new Language(BATCH, "Batch"));
        return map;
    }

    private static List<String> concat(List<String> a, List<String> b)
    {
        List<String> result = new java.util.ArrayList<>(a);
        result.addAll(b);
        return result;
    }

    private static SyntaxHighlighter generic(List<String> keywords, List<String> lineComments)
    {
        return new GenericSyntaxHighlighter(new LanguageSpec(keywords, lineComments));
    }

    private static SyntaxHighlighter generic(List<String> keywords, List<String> lineComments,
                                              String blockStart, String blockEnd)
    {
        return new GenericSyntaxHighlighter(new LanguageSpec(keywords, lineComments, blockStart, blockEnd));
    }

    private static SyntaxHighlighter genericCI(List<String> keywords, List<String> lineComments)
    {
        return new GenericSyntaxHighlighter(new LanguageSpec(keywords, lineComments, null, null, true));
    }

    private static SyntaxHighlighter genericCI(List<String> keywords, List<String> lineComments,
                                                String blockStart, String blockEnd)
    {
        return new GenericSyntaxHighlighter(new LanguageSpec(keywords, lineComments, blockStart, blockEnd, true));
    }
}
