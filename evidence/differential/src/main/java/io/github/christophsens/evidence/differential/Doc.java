package io.github.christophsens.evidence.differential;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A neutral JSON tree that is serialized to text, so every implementation parses the same bytes. */
sealed interface Doc {

    /** A JSON object with unique member names, in insertion order. */
    record Obj(List<Map.Entry<String, Doc>> members) implements Doc {}

    record Arr(List<Doc> items) implements Doc {}

    record Str(String value) implements Doc {}

    /** A number as it is written in the JSON text (for example {@code -0.0} or {@code 1E400}). */
    record Num(String literal) implements Doc {}

    record Lit(String literal) implements Doc {}

    default String toJson() {
        StringBuilder sb = new StringBuilder();
        write(sb);
        return sb.toString();
    }

    private void write(StringBuilder sb) {
        if (this instanceof Obj o) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Doc> e : o.members()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, e.getKey());
                sb.append(':');
                e.getValue().write(sb);
            }
            sb.append('}');
        } else if (this instanceof Arr a) {
            sb.append('[');
            for (int i = 0; i < a.items().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                a.items().get(i).write(sb);
            }
            sb.append(']');
        } else if (this instanceof Str s) {
            quote(sb, s.value());
        } else if (this instanceof Num n) {
            sb.append(n.literal());
        } else if (this instanceof Lit l) {
            sb.append(l.literal());
        }
    }

    /** Writes a JSON string. Non-ASCII is written literally; control characters are escaped. */
    static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** All direct children, for the minimizer. */
    default List<Doc> children() {
        List<Doc> out = new ArrayList<>();
        if (this instanceof Obj o) {
            o.members().forEach(e -> out.add(e.getValue()));
        } else if (this instanceof Arr a) {
            out.addAll(a.items());
        }
        return out;
    }
}
