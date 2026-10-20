package io.github.christophsens.evidence.benchmarks;

/** A query in RFC 9535 and in Jayway JsonPath's spelling (the same except filter syntax, functions and regular expressions). */
record Query(String name, String rfc, String jayway) {

    static final Query DEFINITE = new Query("definite", "$.store.bicycle.color", "$.store.bicycle.color");
    static final Query WILDCARD = new Query("wildcard", "$.store.book[*].title", "$.store.book[*].title");
    static final Query FILTER = new Query("filter", "$.store.book[?@.price < 10 && @.category == 'fiction'].title",
            "$.store.book[?(@.price < 10 && @.category == 'fiction')].title");
    static final Query DESCENDANT = new Query("descendant", "$..price", "$..price");
    static final Query FUNCTION = new Query("function", "$.store.book[?length(@.tags) == 2 && @.price > 5].title",
            "$.store.book[?(@.tags.length() == 2 && @.price > 5)].title");
    static final Query REGEX = new Query("regex", "$.store.book[?match(@.author, 'H.*')].title",
            "$.store.book[?(@.author =~ /H.*/)].title");

    static final Query[] ALL = {DEFINITE, WILDCARD, FILTER, DESCENDANT, FUNCTION, REGEX};

    static Query byName(String name) {
        for (Query q : ALL) {
            if (q.name().equals(name)) {
                return q;
            }
        }
        throw new IllegalArgumentException(name);
    }
}
