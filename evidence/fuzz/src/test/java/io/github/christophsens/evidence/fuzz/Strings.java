package io.github.christophsens.evidence.fuzz;

final class Strings {

    private Strings() {}

    /** A JSONPath string literal in single quotes (RFC 9535, section 2.3.1.1) for any string without lone surrogates. */
    static String literal(String s) {
        StringBuilder out = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\'' -> out.append("\\'");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('\'').toString();
    }

    static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "... (" + s.length() + " chars)";
    }
}
