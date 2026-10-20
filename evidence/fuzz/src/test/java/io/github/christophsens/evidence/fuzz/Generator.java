package io.github.christophsens.evidence.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

/**
 * Builds a JSON text and a JSONPath query from fuzzer data, so that most inputs are well-formed and reach the
 * evaluator and the streaming matcher rather than the syntax checks (about 97 % of the queries are valid). Choice 0 is
 * always the smallest option, so the generation ends when the fuzzer data runs out (it then returns zeros).
 */
final class Generator {

    private static final String[] NAMES = {"a", "b", "c", "", "ab", "é", "'", "\\", "~/", "\u0000", "😀"};
    private static final String[] NUMBERS = {"0", "1", "-1", "1.0", "1e0", "-0", "2", "10e-1", "0.1",
        "9007199254740991", "-9007199254740991", "1e308", "123456789012345678901234567890", "1E-400",
        "1e1000000"};
    private static final String[] STRINGS = {"", "a", "ab", "abc", "A", "\n", "é", "😀", "",
        "a.c", "[a-z]+", "^ab", "b$", "(a|b)*", "\\p{Lu}", "x{2,3}"};

    private final FuzzedDataProvider data;
    private int budget;

    Generator(FuzzedDataProvider data) {
        this.data = data;
    }

    // ---- documents ----

    String document() {
        budget = 400;
        StringBuilder out = new StringBuilder();
        if (data.consumeInt(0, 15) == 15) {
            // Deep nesting, across the depth limits of the parsers (1,000) and of the evaluation (MAX_DEPTH, 1,000).
            int depth = data.consumeInt(1, 1_100);
            boolean objects = data.consumeBoolean();
            for (int i = 0; i < depth; i++) {
                out.append(objects ? "{\"a\":" : "[");
            }
            value(out, 0);
            for (int i = 0; i < depth; i++) {
                out.append(objects ? '}' : ']');
            }
            return out.toString();
        }
        value(out, 0);
        return out.toString();
    }

    private void value(StringBuilder out, int depth) {
        budget--;
        int kind = budget <= 0 || depth > 8 ? data.consumeInt(0, 4) : data.consumeInt(0, 6);
        switch (kind) {
            case 0 -> out.append(pick(NUMBERS));
            case 1 -> string(out, pick(STRINGS));
            case 2 -> out.append("true");
            case 3 -> out.append("false");
            case 4 -> out.append("null");
            case 5 -> {
                out.append('[');
                int n = data.consumeInt(0, 6);
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    value(out, depth + 1);
                }
                out.append(']');
            }
            default -> {
                out.append('{');
                int n = data.consumeInt(0, 5);
                for (int i = 0; i < n; i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    // Duplicate names are possible on purpose: the matcher reports every occurrence.
                    string(out, pick(NAMES));
                    out.append(':');
                    value(out, depth + 1);
                }
                out.append('}');
            }
        }
    }

    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    // ---- queries ----

    String query() {
        budget = 60;
        StringBuilder out = new StringBuilder("$");
        segments(out, 0);
        return out.toString();
    }

    private void segments(StringBuilder out, int nesting) {
        int n = data.consumeInt(0, 4);
        for (int i = 0; i < n && budget > 0; i++) {
            segment(out, nesting);
        }
    }

    private void segment(StringBuilder out, int nesting) {
        budget--;
        switch (data.consumeInt(0, 5)) {
            case 0 -> out.append('.').append(pick(new String[] {"a", "b", "c", "ab"}));
            case 1 -> out.append(".*");
            case 2 -> out.append("..").append(data.consumeBoolean() ? "*" : pick(new String[] {"a", "b"}));
            case 3 -> {
                out.append("..");
                bracket(out, nesting);
            }
            default -> bracket(out, nesting);
        }
    }

    private void bracket(StringBuilder out, int nesting) {
        out.append('[');
        int n = data.consumeInt(1, 3);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                out.append(',');
            }
            selector(out, nesting);
        }
        out.append(']');
    }

    private void selector(StringBuilder out, int nesting) {
        budget--;
        int kind = budget <= 0 || nesting > 4 ? data.consumeInt(0, 3) : data.consumeInt(0, 4);
        switch (kind) {
            case 0 -> out.append(Strings.literal(pick(NAMES)));
            case 1 -> out.append(index());
            case 2 -> out.append('*');
            case 3 -> {
                if (data.consumeBoolean()) {
                    out.append(index());
                }
                out.append(':');
                if (data.consumeBoolean()) {
                    out.append(index());
                }
                if (data.consumeBoolean()) {
                    out.append(':');
                    if (data.consumeBoolean()) {
                        out.append(index());
                    }
                }
            }
            default -> {
                out.append('?');
                logical(out, nesting + 1);
            }
        }
    }

    private String index() {
        return switch (data.consumeInt(0, 4)) {
            case 0 -> String.valueOf(data.consumeInt(0, 6));
            case 1 -> String.valueOf(-data.consumeInt(1, 6));
            case 2 -> data.consumeInt(0, 9) == 9 ? "-0" : "0"; // "-0" is not a valid index
            case 3 -> data.consumeBoolean() ? "9007199254740991" : "-9007199254740991";
            default -> String.valueOf(data.consumeInt(-1_000, 1_000));
        };
    }

    private void logical(StringBuilder out, int nesting) {
        budget--;
        int kind = budget <= 0 || nesting > 6 ? data.consumeInt(0, 1) : data.consumeInt(0, 5);
        switch (kind) {
            case 0 -> relativeQuery(out, nesting);
            case 1 -> comparison(out, nesting);
            case 2 -> {
                out.append('!');
                if (data.consumeBoolean()) {
                    relativeQuery(out, nesting);
                } else {
                    out.append('(');
                    logical(out, nesting + 1);
                    out.append(')');
                }
            }
            case 3 -> {
                logical(out, nesting);
                out.append(data.consumeBoolean() ? " && " : " || ");
                logical(out, nesting);
            }
            case 4 -> {
                out.append('(');
                logical(out, nesting + 1);
                out.append(')');
            }
            default -> {
                out.append(data.consumeBoolean() ? "match(" : "search(");
                singular(out);
                out.append(", ");
                if (data.consumeBoolean()) {
                    out.append(Strings.literal(pick(STRINGS)));
                } else {
                    singular(out);
                }
                out.append(')');
            }
        }
    }

    private void comparison(StringBuilder out, int nesting) {
        comparable(out, nesting);
        out.append(' ').append(pick(new String[] {"==", "!=", "<", "<=", ">", ">="})).append(' ');
        comparable(out, nesting);
    }

    private void comparable(StringBuilder out, int nesting) {
        switch (data.consumeInt(0, 4)) {
            case 0 -> out.append(pick(NUMBERS).replace("1E-400", "1e-4"));
            case 1 -> singular(out);
            case 2 -> out.append(Strings.literal(pick(STRINGS)));
            case 3 -> out.append(pick(new String[] {"true", "false", "null"}));
            default -> {
                switch (data.consumeInt(0, 2)) {
                    case 0 -> {
                        out.append("length(");
                        if (data.consumeBoolean()) {
                            singular(out);
                        } else {
                            out.append(Strings.literal(pick(STRINGS)));
                        }
                        out.append(')');
                    }
                    case 1 -> {
                        out.append("count(");
                        relativeQuery(out, nesting);
                        out.append(')');
                    }
                    default -> {
                        out.append("value(");
                        relativeQuery(out, nesting);
                        out.append(')');
                    }
                }
            }
        }
    }

    /** A singular query: names and indices only. */
    private void singular(StringBuilder out) {
        out.append(data.consumeInt(0, 5) == 5 ? '$' : '@');
        int n = data.consumeInt(0, 3);
        for (int i = 0; i < n; i++) {
            if (data.consumeBoolean()) {
                out.append('.').append(pick(new String[] {"a", "b", "c"}));
            } else {
                out.append('[').append(index()).append(']');
            }
        }
    }

    private void relativeQuery(StringBuilder out, int nesting) {
        out.append(data.consumeInt(0, 5) == 5 ? '$' : '@');
        segments(out, nesting);
    }

    private String pick(String[] options) {
        return options[data.consumeInt(0, options.length - 1)];
    }
}
