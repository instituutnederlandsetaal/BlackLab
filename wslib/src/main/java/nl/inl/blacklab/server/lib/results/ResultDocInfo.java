package nl.inl.blacklab.server.lib.results;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.util.BitSet;

import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;

public class ResultDocInfo {

    private final BlackLabIndex index;

    private String docPid;

    private Document document;

    private final Collection<MetadataField> metadataToInclude;

    private Map<String, List<String>> metadata;

    static class Fragment {
        private final String field;
        private final int start;
        private final int end;
        private final Map<String, List<String>> metadata;

        public Fragment(String field, int start, int end, Map<String, List<String>> metadata) {
            this.field = field;
            this.start = start;
            this.end = end;
            this.metadata = metadata;
        }

        public String getField() {
            return field;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public Map<String, List<String>> getMetadata() {
            return metadata;
        }
    }

    private List<Fragment> fragments = Collections.emptyList();

    private final Map<String, Integer> lengthInTokensPerField = new LinkedHashMap<>();

    private boolean mayView;

    ResultDocInfo(BlackLabIndex index, String docPid, Document document,
            Collection<MetadataField> metadataToInclude) throws BlsException {
        this.index = index;
        this.metadataToInclude = metadataToInclude;
        initDoc(docPid, document);
        getDocInfo();
    }

    private void initDoc(String docPid, Document document) throws BlsException {
        if (docPid == null)
            throw new IllegalArgumentException("Must specify docPid!");
        if (document == null) {
            this.docPid = docPid;
            if (docPid.isEmpty())
                throw new BadRequest("NO_DOC_ID", "Specify document pid.");
            int luceneDocId = index.getDocIdFromPid(docPid);
            if (luceneDocId < 0 || luceneDocId >= index.reader().maxDoc())
                throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
            this.document = index.luceneDoc(luceneDocId);
            if (this.document == null)
                throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                        "INTERR_FETCHING_DOCUMENT_INFO");

            if (index.metadata().metadataFields().anyOccurInFragments()) {
                // Find fragments for this document
                this.fragments = new ArrayList<>();
                try {
                    // Find the segment the doc and its fragments are in
                    BitSetProducer fullDocsBitSetProducer = new QueryBitSetProducer(
                            BLInputDocument.docTypeQuery(BLInputDocument.DocType.DOCUMENT));
                    LeafReaderContext leafReaderContext = index.getLeafReaderContext(luceneDocId);
                    // Determine the first fragment for this document in this segment
                    BitSet fullDocs = fullDocsBitSetProducer.getBitSet(leafReaderContext);
                    int docIdInSegment = luceneDocId - leafReaderContext.docBase;
                    int fragDocId = fullDocs.prevSetBit(docIdInSegment - 1) + 1;
                    // Get the doc values for the fragment fields
                    SortedDocValues dvAnnotatedField = leafReaderContext.reader().getSortedDocValues(BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD);
                    dvAnnotatedField.advanceExact(fragDocId);
                    NumericDocValues dvFragStart = leafReaderContext.reader()
                            .getNumericDocValues(BLInputDocument.FRAG_FIELD_START);
                    dvFragStart.advanceExact(fragDocId);
                    NumericDocValues dvFragEnd = leafReaderContext.reader()
                            .getNumericDocValues(BLInputDocument.FRAG_FIELD_END);
                    dvFragEnd.advanceExact(fragDocId);
                    while (fragDocId < docIdInSegment) {
                        // Fetch the fragment
                        Document fragDoc = leafReaderContext.reader().storedFields().document(fragDocId);
                        Map<String, List<String>> fragMeta = getMetadataFromDoc(fragDoc);
                        String field = dvAnnotatedField.lookupOrd(dvAnnotatedField.ordValue()).utf8ToString();
                        int start = (int) dvFragStart.longValue();
                        int end = (int) dvFragEnd.longValue();
                        fragments.add(new Fragment(field, start, end, fragMeta));
                        // Go to the next fragment
                        fragDocId++;
                        dvAnnotatedField.nextDoc();
                        dvFragStart.nextDoc();
                        dvFragEnd.nextDoc();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        } else {
            this.document = document;
        }
    }

    private void getDocInfo() throws BlsException {
        metadata = getMetadataFromDoc(document);
        for (AnnotatedField f: index.annotatedFields()) {
            Integer length = null;
            if (f.tokenLengthField() != null) {
                String strDocLength = document.get(f.tokenLengthField());
                length = strDocLength == null ? 0 :
                        Integer.parseInt(strDocLength) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            }
            lengthInTokensPerField.put(f.name(), length);
        }

        mayView = index.mayView(document);

    }

    private Map<String, List<String>> getMetadataFromDoc(Document document) {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        for (MetadataField f: metadataToInclude) {
            if (f.name().equals("lengthInTokens") || f.name().equals("mayView"))
                continue;
            String[] values = document.getValues(f.name());
            if (values.length == 0)
                continue;
            metadata.put(f.name(), List.of(values));
        }
        return metadata;
    }

    public String getPid() {
        return docPid;
    }

    public Map<String, List<String>> getMetadata() {
        return metadata;
    }

    public List<Fragment> getFragments() {
        return fragments;
    }

    public Integer getLengthInTokens() {
        return lengthInTokensPerField.get(index.mainAnnotatedField().name());
    }

    public Map<String, Integer> getLengthInTokensPerField() {
        return Collections.unmodifiableMap(lengthInTokensPerField);
    }

    public boolean isMayView() {
        return mayView;
    }
}
