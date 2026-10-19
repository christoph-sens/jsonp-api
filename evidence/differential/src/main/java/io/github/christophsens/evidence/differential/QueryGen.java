package io.github.christophsens.evidence.differential;

import java.util.SplittableRandom;

/**
 * Seeded generator of RFC 9535 queries: all selector kinds, child and descendant segments, filters with comparisons,
 * logical operators, the five functions, slices with negative and zero steps. A small share of the output is
 * ill-typed or mutated on purpose, so the implementations are also compared on which queries they reject.
 */
final class QueryGen {

    private static final String[] INDEXES = {"0", "1", "2", "3", "-1", "-2", "-3", "5", "-5", "-0", "9007199254740991", "-9007199254740991"};
    private static final String[] SLICE_BOUNDS = {"", "", "0", "1", "2", "3", "-1", "-2", "-3", "5", "-5", "9007199254740991", "-9007199254740991"};
    private static final String[] STEPS = {"", "", "1", "2", "3", "-1", "-2", "-3", "0", "9007199254740991", "-9007199254740991"};
    private static final String[] REGEX_ATOMS = {
        "a", "b", "x", "abc", ".", "[a-c]", "[^a]", "\\d", "\\w", "\\s", "\\p{L}", "\\P{L}", "\\p{Lu}", "\\p{N}", "é", "日",
        "😀", "[a-z]", "[\\p{L}]", "\\.", "\\\\", "\\n", "(ab)", "(a|b)", "(a|bc)", "[😀-😁]", "[\\u0061]", "\\-", "[a\\-z]"
    };
    private static final String[] REGEX_QUANT = {"", "", "", "*", "+", "?", "{2}", "{1,2}", "{0,1}", "{2,}", "{0}"};

    /** Lexical edges inside string literals: valid and invalid escapes, lone surrogates, raw control characters. */
    private static final String[] STRING_EDGES = {
        "\\uD800", "\\uDC00", "\\uD83D", "\\uDE00\\uD83D", "\\uD83D\\uDE00", "\\ud83d\\ude00", "\\u0000", "\\a", "\\u12", "\\u123g",
        "\\'", "\\\"", "\\/", "\\x41", "\u0001", "\n", "\t", "\u007f", "\u2028", "\\U0041", "\\"
    };
    private static final String[] NUMBER_EDGES = {
        "01", "-01", "+1", "1.", ".5", "1e", "1E+", "0x1", "-", "--1", "1_0", "00", "-00", "1.5e3", "1e-0", "- 1", "NaN", "Infinity", "1e5e5"
    };

    private final SplittableRandom r;

    QueryGen(SplittableRandom r) {
        this.r = r;
    }

    String query() {
        StringBuilder sb = new StringBuilder("$");
        int n = r.nextInt(100) < 8 ? 0 : 1 + r.nextInt(4);
        for (int i = 0; i < n; i++) {
            segment(sb, 2);
        }
        String q = sb.toString();
        return r.nextInt(100) < 3 ? mutate(q) : q;
    }

    // ---- segments and selectors

    private void segment(StringBuilder sb, int filterDepth) {
        ws(sb, 5);
        boolean descendant = r.nextInt(100) < 35;
        int p = r.nextInt(100);
        if (descendant) {
            sb.append("..");
            if (p < 30) {
                sb.append('*');
            } else if (p < 55) {
                shorthandOrBracket(sb, key());
            } else {
                bracket(sb, filterDepth);
            }
        } else if (p < 20) {
            sb.append(".*");
        } else if (p < 50) {
            sb.append('.');
            shorthandName(sb, key());
        } else {
            bracket(sb, filterDepth);
        }
    }

    private void shorthandOrBracket(StringBuilder sb, String key) {
        if (isShorthand(key)) {
            sb.append(key);
        } else {
            sb.append('[');
            nameLiteral(sb, key);
            sb.append(']');
        }
    }

    /** After a single dot: the shorthand if the name allows it (otherwise an invalid query, on purpose some of the time). */
    private void shorthandName(StringBuilder sb, String key) {
        if (isShorthand(key) || r.nextInt(10) == 0) {
            sb.append(key);
        } else {
            sb.setLength(sb.length() - 1);
            sb.append('[');
            nameLiteral(sb, key);
            sb.append(']');
        }
    }

    static boolean isShorthand(String s) {
        if (s.isEmpty()) {
            return false;
        }
        int[] cps = s.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int c = cps[i];
            boolean first = c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= 0x80 && c <= 0xD7FF)
                    || (c >= 0xE000 && c <= 0x10FFFF);
            boolean rest = first || (c >= '0' && c <= '9');
            if (i == 0 ? !first : !rest) {
                return false;
            }
        }
        return true;
    }

    private void bracket(StringBuilder sb, int filterDepth) {
        sb.append('[');
        ws(sb, 5);
        int n = r.nextInt(100) < 80 ? 1 : 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                ws(sb, 5);
                sb.append(',');
                ws(sb, 5);
            }
            selector(sb, filterDepth);
        }
        ws(sb, 5);
        sb.append(']');
    }

    private void selector(StringBuilder sb, int filterDepth) {
        int p = r.nextInt(100);
        if (p < 24) {
            nameLiteral(sb, key());
        } else if (p < 32) {
            sb.append('*');
        } else if (p < 48) {
            sb.append(r.nextInt(100) < 4 ? pick(NUMBER_EDGES) : pick(INDEXES));
        } else if (p < 66) {
            slice(sb);
        } else if (filterDepth > 0) {
            sb.append('?');
            ws(sb, 5);
            logicalOr(sb, filterDepth, 3);
        } else {
            sb.append(pick(INDEXES));
        }
    }

    private void slice(StringBuilder sb) {
        sb.append(pick(SLICE_BOUNDS));
        ws(sb, 5);
        sb.append(':');
        ws(sb, 5);
        sb.append(pick(SLICE_BOUNDS));
        if (r.nextInt(100) < 60) {
            ws(sb, 5);
            sb.append(':');
            ws(sb, 5);
            sb.append(pick(STEPS));
        }
    }

    // ---- filters

    private void logicalOr(StringBuilder sb, int filterDepth, int d) {
        logicalAnd(sb, filterDepth, d);
        while (d > 0 && r.nextInt(100) < 18) {
            ws(sb, 3);
            sb.append("||");
            ws(sb, 3);
            logicalAnd(sb, filterDepth, d - 1);
        }
    }

    private void logicalAnd(StringBuilder sb, int filterDepth, int d) {
        basic(sb, filterDepth, d);
        while (d > 0 && r.nextInt(100) < 18) {
            ws(sb, 3);
            sb.append("&&");
            ws(sb, 3);
            basic(sb, filterDepth, d - 1);
        }
    }

    private void basic(StringBuilder sb, int filterDepth, int d) {
        int p = r.nextInt(100);
        if (d > 0 && p < 10) {
            sb.append('!');
            ws(sb, 2);
            if (r.nextBoolean()) {
                paren(sb, filterDepth, d - 1);
            } else {
                test(sb, filterDepth);
            }
        } else if (d > 0 && p < 18) {
            paren(sb, filterDepth, d - 1);
        } else if (p < 55) {
            comparison(sb, filterDepth);
        } else {
            test(sb, filterDepth);
        }
    }

    private void paren(StringBuilder sb, int filterDepth, int d) {
        sb.append('(');
        ws(sb, 3);
        logicalOr(sb, filterDepth, d);
        ws(sb, 3);
        sb.append(')');
    }

    private void test(StringBuilder sb, int filterDepth) {
        int p = r.nextInt(100);
        if (p < 55) {
            filterQuery(sb, filterDepth);
        } else if (p < 75) {
            regexFunction(sb, filterDepth);
        } else if (p < 94) {
            // existence of a path with a filter inside
            filterQuery(sb, filterDepth);
        } else if (p < 97) {
            // unknown function, wrong arity, wrong case
            sb.append(pick("foo(@)", "length()", "count(1)", "Match(@.a, 'a')", "match(@.a)", "search(@.a, 'a', 'b')", "count(@, @)",
                    "value(@.a, @.b)", "match ( @.a, 'a')", "length (@)", "value(1)", "count('a')", "match(@.a, 'a') == true",
                    "length(@.a) > 0 && foo", "@.a == match(@.a, 'a')", "!length(@.a)"));
        } else {
            // ill-typed on purpose: a value-typed function as a test
            valueFunction(sb, filterDepth);
        }
    }

    private void comparison(StringBuilder sb, int filterDepth) {
        comparable(sb, filterDepth);
        ws(sb, 3);
        sb.append(pick("==", "!=", "<", "<=", ">", ">=", "==", "=="));
        ws(sb, 3);
        comparable(sb, filterDepth);
    }

    private void comparable(StringBuilder sb, int filterDepth) {
        int p = r.nextInt(100);
        if (p < 22) {
            literal(sb);
        } else if (p < 70) {
            singularQuery(sb);
        } else if (p < 90) {
            valueFunction(sb, filterDepth);
        } else if (p < 97) {
            literal(sb);
        } else {
            // ill-typed on purpose: a non-singular query as a comparable, or a logical function
            if (r.nextBoolean()) {
                filterQuery(sb, filterDepth);
            } else {
                regexFunction(sb, filterDepth);
            }
        }
    }

    private void literal(StringBuilder sb) {
        int p = r.nextInt(100);
        if (r.nextInt(100) < 3) {
            sb.append(pick(NUMBER_EDGES));
        } else if (p < 45) {
            sb.append(r.nextInt(4) == 0 ? pick(DocGen.NUMBERS) : pick("0", "1", "2", "3", "10", "-1", "1.5", "0.1", "-0", "1e2", "1E-2"));
        } else if (p < 80) {
            stringLiteral(sb, pick(DocGen.STRINGS));
        } else {
            sb.append(pick("true", "false", "null"));
        }
    }

    private void singularQuery(StringBuilder sb) {
        sb.append(r.nextInt(100) < 85 ? '@' : '$');
        int n = r.nextInt(4);
        for (int i = 0; i < n; i++) {
            ws(sb, 2);
            int p = r.nextInt(100);
            if (p < 40) {
                sb.append('.');
                shorthandName(sb, key());
            } else if (p < 70) {
                sb.append('[');
                nameLiteral(sb, key());
                sb.append(']');
            } else {
                sb.append('[').append(pick(INDEXES)).append(']');
            }
        }
    }

    /** A relative or absolute query that may select many nodes. */
    private void filterQuery(StringBuilder sb, int filterDepth) {
        sb.append(r.nextInt(100) < 85 ? '@' : '$');
        int n = r.nextInt(3);
        for (int i = 0; i < n; i++) {
            segment(sb, filterDepth - 1 > 0 ? filterDepth - 1 : 0);
        }
    }

    private void valueFunction(StringBuilder sb, int filterDepth) {
        int p = r.nextInt(100);
        if (p < 40) {
            sb.append("length(");
            ws(sb, 2);
            int a = r.nextInt(100);
            if (a < 70) {
                singularQuery(sb);
            } else if (a < 90) {
                stringLiteral(sb, pick(DocGen.STRINGS));
            } else if (a < 95) {
                valueFunction(sb, 0);
            } else {
                filterQuery(sb, filterDepth); // ill-typed on purpose
            }
        } else if (p < 70) {
            sb.append("count(");
            ws(sb, 2);
            filterQuery(sb, filterDepth);
        } else {
            sb.append("value(");
            ws(sb, 2);
            filterQuery(sb, filterDepth);
        }
        ws(sb, 2);
        sb.append(')');
    }

    private void regexFunction(StringBuilder sb, int filterDepth) {
        sb.append(r.nextBoolean() ? "match(" : "search(");
        ws(sb, 2);
        if (r.nextInt(100) < 85) {
            singularQuery(sb);
        } else {
            stringLiteral(sb, pick(DocGen.STRINGS));
        }
        ws(sb, 2);
        sb.append(',');
        ws(sb, 2);
        int p = r.nextInt(100);
        if (p < 80) {
            stringLiteral(sb, regex());
        } else if (p < 90) {
            singularQuery(sb); // pattern from data
        } else if (p < 96) {
            stringLiteral(sb, pick("(", "[", "a{2", "*", "\\", "(?:a)", "\\d+(?=a)", "[b-a]", "a{3,2}", "\\p{Foo}", ""));
        } else {
            filterQuery(sb, filterDepth); // ill-typed on purpose
        }
        ws(sb, 2);
        sb.append(')');
    }

    private String regex() {
        StringBuilder sb = new StringBuilder();
        if (r.nextInt(5) == 0) {
            sb.append('^');
        }
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            sb.append(pick(REGEX_ATOMS)).append(pick(REGEX_QUANT));
        }
        if (r.nextInt(5) == 0) {
            sb.append('$');
        }
        return sb.toString();
    }

    // ---- lexical pieces

    private String key() {
        int p = r.nextInt(100);
        if (p < 70) {
            return DocGen.KEYS[r.nextInt(8)];
        }
        return DocGen.KEYS[r.nextInt(DocGen.KEYS.length)];
    }

    /** A name selector in single or double quotes with escapes chosen at random. */
    private void nameLiteral(StringBuilder sb, String key) {
        stringLiteral(sb, key);
    }

    private void stringLiteral(StringBuilder sb, String s) {
        boolean single = r.nextBoolean();
        char q = single ? '\'' : '"';
        sb.append(q);
        s.codePoints().forEach(cp -> {
            switch (cp) {
                case '\'' -> sb.append(single ? "\\'" : "'");
                case '"' -> sb.append(single ? "\"" : "\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '/' -> sb.append(r.nextBoolean() ? "\\/" : "/");
                default -> {
                    if (cp < 0x20) {
                        sb.append(String.format("\\u%04x", cp));
                    } else if (cp > 0x7f && r.nextInt(100) < 30) {
                        if (cp > 0xFFFF) {
                            char[] pair = Character.toChars(cp);
                            sb.append(String.format(r.nextBoolean() ? "\\u%04x\\u%04x" : "\\u%04X\\u%04X", (int) pair[0], (int) pair[1]));
                        } else {
                            sb.append(String.format(r.nextBoolean() ? "\\u%04x" : "\\u%04X", cp));
                        }
                    } else {
                        sb.appendCodePoint(cp);
                    }
                }
            }
        });
        if (r.nextInt(100) < 2) {
            sb.append(pick(STRING_EDGES));
        }
        sb.append(q);
    }

    private void ws(StringBuilder sb, int oneIn) {
        if (r.nextInt(100) < oneIn) {
            int n = 1 + r.nextInt(2);
            for (int i = 0; i < n; i++) {
                sb.append(" \t\n\r".charAt(r.nextInt(4)));
            }
        }
    }

    private String mutate(String q) {
        String junk = "[]().?$@'\"*:,!=<>&| \\-.0e";
        int pos = r.nextInt(q.length() + 1);
        return switch (r.nextInt(6)) {
            case 4 -> pick(" ", "\u00a0", "\u000b", "\f", "\n") + q;
            case 5 -> q.substring(0, pos) + pick("\u00a0", "\u000b", "\f", "\u2003", "\u0000") + q.substring(pos);
            case 0 -> q.substring(0, pos) + junk.charAt(r.nextInt(junk.length())) + q.substring(pos);
            case 1 -> pos < q.length() ? q.substring(0, pos) + q.substring(pos + 1) : q;
            case 2 -> q.substring(0, pos);
            default -> q + pick(" ", "\n", "]", "..", ".", ",");
        };
    }

    private String pick(String... a) {
        return a[r.nextInt(a.length)];
    }
}
