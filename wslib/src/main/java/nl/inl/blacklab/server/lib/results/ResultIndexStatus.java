package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.index.Index;

public class ResultIndexStatus {
    private final Index index;
    private final IndexMetadata metadata;
    private final long files;
    private final long docs;
    private final long tokens;
    private final Index.IndexStatus indexStatus;

    ResultIndexStatus(Index index, long files, long docs, long tokens) {
        this.index = index;
        this.metadata = index.getIndexMetadata();
        this.files = files;
        this.docs = docs;
        this.tokens = tokens;
        this.indexStatus = index.getStatus();
    }

    public Index getIndex() {
        return index;
    }

    public IndexMetadata getMetadata() {
        return metadata;
    }

    public long getFiles() {
        return files;
    }

    public long getDocs() {
        return docs;
    }

    public long getTokens() {
        return tokens;
    }

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }

}
