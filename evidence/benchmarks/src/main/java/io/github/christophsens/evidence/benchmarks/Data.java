package io.github.christophsens.evidence.benchmarks;

import java.util.Random;

/** Deterministic test documents as JSON text. */
final class Data {

    private static final String[] AUTHORS = {"Nigel Rees", "Evelyn Waugh", "Herman Melville", "J. R. R. Tolkien"};
    private static final String[] CATEGORIES = {"reference", "fiction"};

    private Data() {}

    /** A bookstore with the given number of books ({@code Random(9535)}; the shape of the classic example). */
    static String store(int books) {
        Random random = new Random(9535);
        StringBuilder sb = new StringBuilder(books * 160).append("{\"store\":{\"book\":[");
        for (int i = 0; i < books; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"category\":\"").append(CATEGORIES[random.nextInt(CATEGORIES.length)])
                    .append("\",\"author\":\"").append(AUTHORS[random.nextInt(AUTHORS.length)])
                    .append("\",\"title\":\"Book ").append(i).append('"');
            if (random.nextInt(3) == 0) {
                sb.append(",\"isbn\":\"0-").append(random.nextInt(1000)).append('-').append(random.nextInt(100000)).append('-')
                        .append(random.nextInt(10)).append('"');
            }
            sb.append(",\"price\":").append(Math.round(random.nextDouble() * 3000) / 100.0)
                    .append(",\"tags\":[\"t").append(random.nextInt(5)).append("\",\"t").append(random.nextInt(5)).append("\"]}");
        }
        return sb.append("],\"bicycle\":{\"color\":\"red\",\"price\":399}}}").toString();
    }
}
