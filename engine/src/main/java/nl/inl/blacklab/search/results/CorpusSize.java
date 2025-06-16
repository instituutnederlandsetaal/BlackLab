package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CorpusSize {

    public static final CorpusSize EMPTY = new CorpusSize(Count.create(), null);

    public static CorpusSize get(long documents, long tokens, Map<String, Count> tokensPerField) {
        return new CorpusSize(Count.get(documents, tokens), tokensPerField);
    }

    public static class Count {
        public static Count get(long documents, long tokens) {
            return new Count(documents, tokens);
        }

        public static Count create() {
            return new Count(0, 0);
        }

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

    private CorpusSize(Count total, Map<String, Count> perField) {
        super();
        this.total = total;
        this.perField = perField == null ? new LinkedHashMap<>() : perField;
    }

    public Count getTotalCount() {
        return total;
    }

    public Map<String, Count> getTokensPerField() {
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
