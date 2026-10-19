package io.github.christophsens.evidence.differential;

import io.github.christophsens.evidence.differential.Engines.Compiled;
import io.github.christophsens.evidence.differential.Engines.Engine;
import io.github.christophsens.evidence.differential.Engines.Hit;
import io.github.christophsens.evidence.differential.Engines.JsonpEngine;
import io.github.christophsens.evidence.differential.Engines.Result;
import io.github.christophsens.evidence.differential.Engines.TreetrailEngine;
import jakarta.json.JsonPath;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Runs generated (document, query) cases through Parsson, Johnzon and Treetrail and records every disagreement.
 *
 * <p>Compared: whether the query compiles; the selected nodes with normalized path, JSON Pointer and value
 * (ordered; a different order of the same nodes is counted separately, because RFC 9535 leaves the order of object
 * members unspecified); that a node's JSON Pointer leads back to its value; and, for streamable queries, the
 * streaming matcher of Parsson against the one of Johnzon, and each against its own tree evaluation.
 */
final class Differential {

    static final JsonpEngine PARSSON = new JsonpEngine("PARSSON", Engines.PARSSON);
    static final JsonpEngine JOHNZON = new JsonpEngine("JOHNZON", Engines.JOHNZON);
    static final Engine TREETRAIL = new TreetrailEngine();

    /** A disagreement, grouped by its signature. */
    record Diff(String signature, long caseSeed, String document, String query, String detail) {}

    static final class Report {
        long documents;
        long queries;
        long compiled;
        long rejectedByAll;
        long compared;
        long orderOnly;
        long streamed;
        long nonEmpty;
        long withFilter;
        long parseDisagreements;
        final Map<String, Integer> counts = new LinkedHashMap<>();
        final Map<String, Diff> first = new LinkedHashMap<>();

        void add(Diff d) {
            counts.merge(d.signature(), 1, Integer::sum);
            first.putIfAbsent(d.signature(), d);
        }

        boolean clean() {
            return counts.isEmpty();
        }

        String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("documents=%d queries=%d compiledByAll=%d rejectedByAll=%d resultsCompared=%d nonEmpty=%d withFilter=%d streamed=%d orderOnly=%d%n",
                    documents, queries, compiled, rejectedByAll, compared, nonEmpty, withFilter, streamed, orderOnly));
            for (var e : counts.entrySet()) {
                Diff d = first.get(e.getKey());
                String doc = d.document();
                if (d.caseSeed() != 0 && !d.query().startsWith("(")) {
                    try {
                        Doc original = new DocGen(new SplittableRandom(d.caseSeed())).document();
                        doc = minimize(original, d.query(), d.signature()).toJson();
                    } catch (RuntimeException ex) {
                        // keep the original document
                    }
                }
                String detail = d.detail().lines().limit(14).reduce((x, y) -> x + "\n" + y).orElse("");
                sb.append(String.format("%n[%s] x%d%n  case seed: %d%n  query: %s%n  minimized document: %s%n  %s%n",
                        e.getKey(), e.getValue(), d.caseSeed(), d.query(), doc, detail.replace("\n", "\n  ")));
            }
            return sb.toString();
        }
    }

    static long caseSeed(long masterSeed, long i) {
        return new SplittableRandom(masterSeed + i * 0x9E3779B97F4A7C15L).nextLong();
    }

    static Report run(long masterSeed, int docs, int queriesPerDoc) {
        Report report = new Report();
        for (int i = 0; i < docs; i++) {
            runCase(caseSeed(masterSeed, i), queriesPerDoc, report);
        }
        return report;
    }

    static Report replay(long caseSeed, int queriesPerDoc) {
        Report report = new Report();
        runCase(caseSeed, queriesPerDoc, report);
        return report;
    }

    static void runCase(long caseSeed, int queriesPerDoc, Report report) {
        SplittableRandom r = new SplittableRandom(caseSeed);
        Doc doc = new DocGen(r).document();
        String json = doc.toJson();
        QueryGen qg = new QueryGen(r);
        report.documents++;

        JsonValue p = PARSSON.parse(json);
        JsonValue j = JOHNZON.parse(json);
        if (!Engines.canon(p).equals(Engines.canon(j))) {
            report.parseDisagreements++;
            report.add(new Diff("parse", caseSeed, json, "(no query)", "PARSSON: " + Engines.canon(p) + "\nJOHNZON: " + Engines.canon(j)));
        }
        for (int q = 0; q < queriesPerDoc; q++) {
            String query = qg.query();
            report.queries++;
            for (Diff d : check(caseSeed, json, p, j, query, report)) {
                report.add(d);
            }
        }
    }

    /** All disagreements for one query on one document. */
    static List<Diff> check(long caseSeed, String json, JsonValue p, JsonValue j, String query, Report report) {
        List<Diff> out = new ArrayList<>();
        Engine[] engines = {PARSSON, JOHNZON, TREETRAIL};
        JsonValue[] docs = {p, j, p};
        Compiled[] compiled = new Compiled[3];
        String[] compileErrors = new String[3];
        for (int i = 0; i < 3; i++) {
            try {
                compiled[i] = engines[i].compile(query);
            } catch (RuntimeException | StackOverflowError e) {
                compileErrors[i] = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).lines().findFirst().orElse("");
            }
        }
        int accepted = 0;
        for (Compiled c : compiled) {
            accepted += c != null ? 1 : 0;
        }
        if (accepted == 0) {
            report.rejectedByAll++;
            return out;
        }
        if (accepted != 3) {
            StringBuilder sb = new StringBuilder();
            StringBuilder sig = new StringBuilder("compile:");
            for (int i = 0; i < 3; i++) {
                sb.append(engines[i].name()).append(": ").append(compiled[i] != null ? "accepted" : "rejected (" + compileErrors[i] + ")").append('\n');
                sig.append(compiled[i] != null ? engines[i].name().charAt(0) : '-');
            }
            out.add(new Diff(sig.toString(), caseSeed, json, query, sb.toString().stripTrailing()));
            return out;
        }
        report.compiled++;

        Result[] results = new Result[3];
        for (int i = 0; i < 3; i++) {
            results[i] = compiled[i].query(docs[i]);
        }
        report.compared++;
        if (!results[0].failed() && !results[0].hits().isEmpty()) {
            report.nonEmpty++;
        }
        if (query.indexOf('?') >= 0) {
            report.withFilter++;
        }
        compareResults(out, caseSeed, json, query, engines, results, report);

        for (JsonpEngine e : new JsonpEngine[] {PARSSON, JOHNZON}) {
            String problem = e.pointerProblem(query, e == PARSSON ? p : j);
            if (problem != null) {
                out.add(new Diff("pointer:" + e.name(), caseSeed, json, query, problem));
            }
        }
        checkStreaming(out, caseSeed, json, query, results, report);
        return out;
    }

    private static void compareResults(List<Diff> out, long caseSeed, String json, String query, Engine[] engines,
            Result[] results, Report report) {
        // Group engines by equal outcome.
        Map<String, List<String>> classes = new LinkedHashMap<>();
        boolean orderOnly = false;
        for (int i = 0; i < 3; i++) {
            String key = results[i].failed() ? "ERROR" : null;
            if (key == null) {
                for (int k = 0; k < i && key == null; k++) {
                    if (!results[k].failed() && results[k].hits().equals(results[i].hits())) {
                        key = key(engines, k);
                    }
                }
                if (key == null) {
                    for (int k = 0; k < i && key == null; k++) {
                        if (!results[k].failed() && multiset(results[k].hits()).equals(multiset(results[i].hits()))) {
                            key = key(engines, k);
                            orderOnly = true;
                        }
                    }
                }
                if (key == null) {
                    key = key(engines, i);
                }
            }
            classes.computeIfAbsent(key, x -> new ArrayList<>()).add(engines[i].name());
        }
        if (orderOnly) {
            report.orderOnly++;
        }
        if (classes.size() == 1 && !classes.containsKey("ERROR")) {
            return;
        }
        if (classes.size() == 1) { // all three failed the same way: fine, but remember it is an evaluation error
            return;
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder sig = new StringBuilder("result:");
        for (int i = 0; i < 3; i++) {
            sb.append(engines[i].name()).append(": ").append(results[i].failed() ? "error " + results[i].error() : describe(results[i].hits())).append('\n');
        }
        for (var e : classes.entrySet()) {
            sig.append(String.join("=", e.getValue().stream().map(s -> s.substring(0, 1)).toList())).append(e.getKey().equals("ERROR") ? "(error)" : "").append(' ');
        }
        out.add(new Diff(sig.toString().trim(), caseSeed, json, query, sb.toString().stripTrailing()));
    }

    private static String key(Engine[] engines, int k) {
        return engines[k].name();
    }

    private static void checkStreaming(List<Diff> out, long caseSeed, String json, String query, Result[] results, Report report) {
        JsonPath pp = Engines.PARSSON.createPath(query);
        JsonPath jp = Engines.JOHNZON.createPath(query);
        JsonPath.Buffering pb = pp.getBuffering();
        JsonPath.Buffering jb = jp.getBuffering();
        if (pb != jb) {
            out.add(new Diff("streaming:buffering", caseSeed, json, query, "PARSSON: " + pb + "\nJOHNZON: " + jb));
            return;
        }
        if (pb == JsonPath.Buffering.UNBOUNDED) {
            Result a = PARSSON.stream(pp, json);
            Result b = JOHNZON.stream(jp, json);
            if (!a.failed() || !b.failed()) {
                out.add(new Diff("streaming:unbounded", caseSeed, json, query,
                        "matcher() of an UNBOUNDED query must throw\nPARSSON: " + describeResult(a) + "\nJOHNZON: " + describeResult(b)));
            }
            return;
        }
        report.streamed++;
        Result a = PARSSON.stream(pp, json);
        Result b = JOHNZON.stream(jp, json);
        if (a.failed() != b.failed() || (!a.failed() && !a.hits().equals(b.hits()))) {
            out.add(new Diff("streaming:P!=J", caseSeed, json, query, "PARSSON: " + describeResult(a) + "\nJOHNZON: " + describeResult(b)));
        }
        if (!results[0].failed() && !a.failed() && !multiset(results[0].hits()).equals(multiset(a.hits()))) {
            out.add(new Diff("streaming:tree!=stream:PARSSON", caseSeed, json, query, "TREE: " + describe(results[0].hits()) + "\nSTREAM: " + describe(a.hits())));
        }
        if (!results[1].failed() && !b.failed() && !multiset(results[1].hits()).equals(multiset(b.hits()))) {
            out.add(new Diff("streaming:tree!=stream:JOHNZON", caseSeed, json, query, "TREE: " + describe(results[1].hits()) + "\nSTREAM: " + describe(b.hits())));
        }
        if (!results[0].failed() && a.failed() && !pb.equals(JsonPath.Buffering.UNBOUNDED)) {
            out.add(new Diff("streaming:error:PARSSON", caseSeed, json, query, "tree ok, stream " + describeResult(a)));
        }
        if (!results[1].failed() && b.failed()) {
            out.add(new Diff("streaming:error:JOHNZON", caseSeed, json, query, "tree ok, stream " + describeResult(b)));
        }
    }

    private static Map<Hit, Integer> multiset(List<Hit> hits) {
        Map<Hit, Integer> m = new HashMap<>();
        for (Hit h : hits) {
            m.merge(h, 1, Integer::sum);
        }
        return m;
    }

    private static String describeResult(Result r) {
        return r.failed() ? "error " + r.error() : describe(r.hits());
    }

    private static String describe(List<Hit> hits) {
        StringBuilder sb = new StringBuilder("[");
        for (Hit h : hits) {
            sb.append("\n    ").append(h.path()).append("  ").append(h.pointer().isEmpty() ? "(root)" : h.pointer()).append("  ").append(h.value());
        }
        return sb.append(hits.isEmpty() ? "]" : "\n  ]").toString();
    }

    // ---- minimizer

    /** Shrinks the document of a difference as long as the same signature is reported. */
    static Doc minimize(Doc doc, String query, String signature) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Doc candidate : candidates(doc)) {
                if (reproduces(candidate, query, signature)) {
                    doc = candidate;
                    progress = true;
                    break;
                }
            }
        }
        return doc;
    }

    private static boolean reproduces(Doc doc, String query, String signature) {
        try {
            String json = doc.toJson();
            for (Diff d : check(0, json, PARSSON.parse(json), JOHNZON.parse(json), query, new Report())) {
                if (d.signature().equals(signature)) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            // does not reproduce
        }
        return false;
    }

    private static List<Doc> candidates(Doc doc) {
        List<Doc> out = new ArrayList<>(doc.children()); // replace by a child
        if (doc instanceof Doc.Obj o) {
            for (int i = 0; i < o.members().size(); i++) {
                List<Map.Entry<String, Doc>> m = new ArrayList<>(o.members());
                m.remove(i);
                out.add(new Doc.Obj(m));
                for (Doc c : candidates(o.members().get(i).getValue())) {
                    List<Map.Entry<String, Doc>> m2 = new ArrayList<>(o.members());
                    m2.set(i, Map.entry(o.members().get(i).getKey(), c));
                    out.add(new Doc.Obj(m2));
                }
            }
        } else if (doc instanceof Doc.Arr a) {
            for (int i = 0; i < a.items().size(); i++) {
                List<Doc> m = new ArrayList<>(a.items());
                m.remove(i);
                out.add(new Doc.Arr(m));
                for (Doc c : candidates(a.items().get(i))) {
                    List<Doc> m2 = new ArrayList<>(a.items());
                    m2.set(i, c);
                    out.add(new Doc.Arr(m2));
                }
            }
        } else if (!(doc instanceof Doc.Lit l && l.literal().equals("null"))) {
            out.add(new Doc.Lit("null"));
        }
        return out;
    }
}
