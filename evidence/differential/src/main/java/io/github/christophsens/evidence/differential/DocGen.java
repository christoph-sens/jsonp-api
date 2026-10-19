package io.github.christophsens.evidence.differential;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Seeded generator of JSON documents with the edge cases that matter for JSONPath. */
final class DocGen {

    /** Member names. The small head makes queries hit; the tail has the awkward ones. */
    static final String[] KEYS = {
        "a", "b", "c", "x", "y", "k", "name", "price", "id", "", "é", "日本", "a b", "a'b", "a\"b", "\\", "\n", "😀",
        "0", "1", "-1", "$", "@", "*", "..", "[", "]", "?", "ß", "é", "é", " "
    };

    static final String[] STRINGS = {
        "", "a", "b", "abc", "abcd", "x", "hello", "Hello", "é", "é", "日本語", "😀", "a😀b", "😀😀", "ab\ncd", "a.b",
        "1", "0", "-1", "true", "null", "10", "éé", "aaaa", "ba", " ", "\u007f", "\u0000", "tab\there",
        "𝄞", "Ünï", "A", "z", "foo bar", "\\", "\"", "'"
    };

    static final String[] NUMBERS = {
        "0", "-0", "0.0", "-0.0", "0e0", "-0e0", "1", "-1", "2", "3", "10", "100", "1.0", "1e0", "1E2", "1E+2", "1e-2",
        "1.5", "-1.5", "0.1", "0.2", "0.30000000000000004", "0.3", "0.5", "9007199254740991", "9007199254740992",
        "9007199254740993", "-9007199254740993", "9223372036854775807", "9223372036854775808",
        "123456789012345678901234567890", "1e400", "-1e400", "1e-400", "4.9e-324", "1.7976931348623157e308",
        "1.7976931348623159e308", "2.5e-1", "12345678901234567890.5", "0.1e1", "1.00", "5e-1", "1e1000000"
    };

    private final SplittableRandom r;

    DocGen(SplittableRandom r) {
        this.r = r;
    }

    Doc document() {
        int p = r.nextInt(100);
        if (p < 3) {
            return scalar();
        }
        if (p < 10) {
            return wideArray();
        }
        if (p < 16) {
            return deepChain();
        }
        return value(4 + r.nextInt(2), p < 50);
    }

    /** Long arrays of small numbers, for slices and negative indexes. */
    private Doc wideArray() {
        int n = 8 + r.nextInt(25);
        List<Doc> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(r.nextInt(8) == 0 ? value(2, true) : new Doc.Num(Integer.toString(i - r.nextInt(3))));
        }
        return new Doc.Arr(items);
    }

    /** Nesting 8 to 20 levels deep, with a sibling at some levels, for descendant segments. */
    private Doc deepChain() {
        Doc d = scalar();
        int depth = 8 + r.nextInt(13);
        for (int i = 0; i < depth; i++) {
            if (r.nextBoolean()) {
                List<Doc> items = new ArrayList<>();
                items.add(d);
                if (r.nextInt(3) == 0) {
                    items.add(scalar());
                }
                d = new Doc.Arr(items);
            } else {
                List<Map.Entry<String, Doc>> m = new ArrayList<>();
                m.add(Map.entry(KEYS[r.nextInt(4)], d));
                if (r.nextInt(3) == 0) {
                    m.add(Map.entry(KEYS[4 + r.nextInt(4)], scalar()));
                }
                d = new Doc.Obj(m);
            }
        }
        return d;
    }

    private Doc value(int depth, boolean regular) {
        int p = r.nextInt(100);
        if (depth <= 0 || p < 35) {
            return scalar();
        }
        return p < 70 ? object(depth, regular) : array(depth, regular);
    }

    private Doc object(int depth, boolean regular) {
        int n = r.nextInt(5);
        Map<String, Doc> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String key = r.nextInt(100) < (regular ? 90 : 60) ? KEYS[r.nextInt(8)] : KEYS[r.nextInt(KEYS.length)];
            m.putIfAbsent(key, value(depth - 1, regular));
        }
        return new Doc.Obj(new ArrayList<>(m.entrySet()));
    }

    private Doc array(int depth, boolean regular) {
        int n = r.nextInt(regular ? 5 : 7);
        List<Doc> items = new ArrayList<>();
        // Arrays of similar objects make filters meaningful.
        boolean uniform = r.nextInt(3) == 0;
        for (int i = 0; i < n; i++) {
            items.add(uniform && r.nextBoolean() ? object(depth - 1, true) : value(depth - 1, regular));
        }
        return new Doc.Arr(items);
    }

    private Doc scalar() {
        int p = r.nextInt(100);
        if (p < 40) {
            return number();
        }
        if (p < 75) {
            return new Doc.Str(string());
        }
        if (p < 85) {
            return new Doc.Lit("null");
        }
        return new Doc.Lit(p < 93 ? "true" : "false");
    }

    private Doc number() {
        if (r.nextInt(4) == 0) {
            return new Doc.Num(Integer.toString(r.nextInt(-5, 12)));
        }
        if (r.nextInt(12) == 0) {
            return new Doc.Num(r.nextInt(-1000, 1000) + "." + r.nextInt(0, 1000));
        }
        return new Doc.Num(NUMBERS[r.nextInt(NUMBERS.length)]);
    }

    private String string() {
        if (r.nextInt(8) == 0) {
            StringBuilder sb = new StringBuilder();
            int n = r.nextInt(6);
            for (int i = 0; i < n; i++) {
                sb.append(STRINGS[r.nextInt(STRINGS.length)]);
            }
            return sb.toString();
        }
        return STRINGS[r.nextInt(STRINGS.length)];
    }
}
