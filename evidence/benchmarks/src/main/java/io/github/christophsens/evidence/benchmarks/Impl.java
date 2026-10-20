package io.github.christophsens.evidence.benchmarks;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JakartaJsonProvider;
import io.github.treetrail.jsonpath.jsonp.JsonpModel;
import jakarta.json.JsonPath;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The four JSONPath implementations behind one shape. All of them query a {@code jakarta.json.JsonValue} tree: the
 * two providers their own, Treetrail and Jayway JsonPath (through its JakartaJsonProvider) the Parsson tree.
 * Every query returns the list of selected values (no paths), so the work per result is the same.
 */
public enum Impl {
    PARSSON(new org.eclipse.parsson.JsonProviderImpl()),
    JOHNZON(new org.apache.johnzon.core.JsonProviderImpl()),
    TREETRAIL(new org.eclipse.parsson.JsonProviderImpl()),
    JAYWAY(new org.eclipse.parsson.JsonProviderImpl());

    private static final Configuration JAYWAY_CONFIGURATION =
            Configuration.builder().jsonProvider(new JakartaJsonProvider())
                    .options(com.jayway.jsonpath.Option.ALWAYS_RETURN_LIST).build(); // a definite path returns a list, like the others

    /** A compiled query. */
    interface Compiled {
        Object query(Object document);
    }

    final JsonProvider provider;

    Impl(JsonProvider provider) {
        this.provider = provider;
    }

    JsonValue parse(String json) {
        try (var reader = provider.createReader(new StringReader(json))) {
            return reader.readValue();
        }
    }

    boolean isJsonpProvider() {
        return this == PARSSON || this == JOHNZON;
    }

    /** Compiles the query; Jayway gets its own spelling of it. */
    Compiled compile(Query q) {
        switch (this) {
            case PARSSON:
            case JOHNZON: {
                JsonPath path = provider.createPath(q.rfc());
                return doc -> {
                    List<Object> out = new ArrayList<>();
                    for (JsonPath.Node n : path.query((JsonValue) doc)) {
                        out.add(n.getValue());
                    }
                    return out;
                };
            }
            case TREETRAIL: {
                io.github.treetrail.jsonpath.JsonPath path = io.github.treetrail.jsonpath.JsonPath.compile(q.rfc());
                return doc -> path.query((JsonValue) doc, JsonpModel.INSTANCE).values();
            }
            default: {
                com.jayway.jsonpath.JsonPath path = com.jayway.jsonpath.JsonPath.compile(q.jayway());
                return doc -> path.read(doc, JAYWAY_CONFIGURATION);
            }
        }
    }

    /** Only the compile step, for the compile benchmark. */
    Object compileOnly(Query q) {
        switch (this) {
            case PARSSON:
            case JOHNZON:
                return provider.createPath(q.rfc());
            case TREETRAIL:
                return io.github.treetrail.jsonpath.JsonPath.compile(q.rfc());
            default:
                return com.jayway.jsonpath.JsonPath.compile(q.jayway());
        }
    }
}
