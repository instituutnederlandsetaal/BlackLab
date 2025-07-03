package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.util.BlsUtils;

public class ResultDocInfo {

    private final BlackLabIndex index;

    private String docPid;

    private Document document;

    private final Collection<MetadataField> metadataToWrite;

    private Map<String, List<String>> metadata;

    private Integer lengthInTokens;

    private final Map<String, Integer> lengthInTokensPerField = new LinkedHashMap<>();

    private boolean mayView;

    ResultDocInfo(BlackLabIndex index, String docPid, Document document,
            Collection<MetadataField> metadataToWrite) throws BlsException {
        this.index = index;
        this.metadataToWrite = metadataToWrite;
        initDoc(docPid, document);
        getDocInfo();
    }

    private void initDoc(String docPid, Document document) throws BlsException {
        if (docPid == null)
            throw new IllegalArgumentException("Must specify docPid!");
        if (document == null) {
            this.docPid = docPid;
            if (docPid.length() == 0)
                throw new BadRequest("NO_DOC_ID", "Specify document pid.");
            int luceneDocId = BlsUtils.getDocIdFromPid(index, docPid);
            if (luceneDocId < 0 || luceneDocId >= index.reader().maxDoc())
                throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
            this.document = index.luceneDoc(luceneDocId);
            if (this.document == null)
                throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                        "INTERR_FETCHING_DOCUMENT_INFO");
        } else {
            this.document = document;
        }
    }

    private void getDocInfo() throws BlsException {
        metadata = new LinkedHashMap<>();
        for (MetadataField f: metadataToWrite) {
            if (f.name().equals("lengthInTokens") || f.name().equals("mayView"))
                continue;
            String[] values = document.getValues(f.name());
            if (values.length == 0)
                continue;
            metadata.put(f.name(), List.of(values));
        }
        // TODO: report token length for all annotated fields?
        String tokenLengthField = index.mainAnnotatedField().tokenLengthField();
        lengthInTokens = null;
        if (tokenLengthField != null) {
            String strDocLength = document.get(tokenLengthField);
            lengthInTokens = strDocLength == null ? 0 :
                    Integer.parseInt(strDocLength) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
        }

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

    public String getPid() {
        return docPid;
    }

    public Map<String, List<String>> getMetadata() {
        return metadata;
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
