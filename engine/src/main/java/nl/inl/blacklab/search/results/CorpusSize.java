package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Size of a (sub)corpus in documents and tokens.
 *
 * Includes counts per annotated field.
 */
public final class CorpusSize {

    public static final CorpusSize EMPTY = new CorpusSize(new Count(0, 0), null);

    public static class Count {

        long documents;
        long tokens;

        public Count(long documents, long tokens) {
            this.documents = documents;
            this.tokens = tokens;
        }

        public void add(long documents, long tokens) {
            this.documents += documents;
            this.tokens += tokens;
        }

        public boolean hasTokenCount() { return tokens >= 0; }

        public boolean hasDocumentCount() { return documents >= 0; }

        @Override
        public String toString() {
            return String.format("CorpusSize(%d docs, %d tokens)", documents, tokens);
        }

        public long getDocuments() {
            return documents;
        }

        public long getTokens() {
            return tokens;
        }
    }

    private final Count total;

    private final Map<String, Count> perField;

    public CorpusSize(Count total, Map<String, Count> perField) {
        this.total = total;
        this.perField = perField == null ? new LinkedHashMap<>() : perField;
    }

    public Count getTotalCount() {
        return total;
    }

    public Map<String, Count> getCountsPerField() {
        return Collections.unmodifiableMap(perField);
    }

    public long getDocumentVersions() {
        if (perField.isEmpty())
            return getTotalCount().getDocuments();
        return perField.values().stream().mapToLong(Count::getDocuments).sum();
    }

    @Override
    public String toString() {
        return "CorpusSize(" + total + ")";
    }

}
