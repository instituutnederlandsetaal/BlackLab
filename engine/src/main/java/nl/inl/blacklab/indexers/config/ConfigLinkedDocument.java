package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Configuration for a linked document. */
public class ConfigLinkedDocument {

    public enum MissingLinkPathAction {
        IGNORE,
        WARN,
        FAIL;

        @JsonCreator
        public static MissingLinkPathAction fromStringValue(String v) {
            return valueOf(v.toUpperCase());
        }

        @JsonValue
        public String stringValue() {
            return toString().toLowerCase();
        }
    }

    /** Linked document type name, and field name if we're going to store it */
    private String name;

    /**
     * Should we store the linked document in the content store? (default: false)
     */
    private boolean store = false;

    /**
     * Where in the document to find the information we need to locate the linked
     * document.
     */
    final List<ConfigLinkValue> linkValues = new ArrayList<>();

    /**
     * What to do if we can't find the link information: ignore, warn or fail
     * (default: fail)
     */
    private MissingLinkPathAction ifLinkPathMissing = MissingLinkPathAction.FAIL;

    /** Format of the linked input file */
    private String inputFormatIdentifier;

    /** File or URL reference to our linked document (or archive containing it) */
    private String inputFile;

    /**
     * If input file is a TAR or ZIP archive, this is the path inside the archive
     */
    private String pathInsideArchive;

    /**
     * Path to our specific document inside this file (if omitted, file must contain
     * exactly one document)
     */
    private String documentPath;

    public ConfigLinkedDocument(String name) {
        this.name = name;
    }

    void validate(InputFormatMessages messages) {
        String t = "linked document";
        messages.mustHave(t, name, "name");
        if (inputFormatIdentifier == null)
            messages.error("linked document must have inputFormat");
        messages.mustHave(t, inputFile, "inputFile");
        for (ConfigLinkValue lv : linkValues) {
            lv.validate(messages);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean shouldStore() {
        return store;
    }

    public void setStore(boolean store) {
        this.store = store;
    }

    public List<ConfigLinkValue> getLinkValues() {
        return Collections.unmodifiableList(linkValues);
    }

    public void addLinkValue(ConfigLinkValue linkValue) {
        this.linkValues.add(linkValue);
    }

    public MissingLinkPathAction getIfLinkPathMissing() {
        return ifLinkPathMissing;
    }

    public void setIfLinkPathMissing(MissingLinkPathAction ifLinkPathMissing) {
        this.ifLinkPathMissing = ifLinkPathMissing;
    }

    public String getInputFormatIdentifier() {
        return inputFormatIdentifier;
    }

    public void setInputFormatIdentifier(String formatIdentifier) {
        this.inputFormatIdentifier = formatIdentifier;
    }

    public String getInputFile() {
        return inputFile;
    }

    public void setInputFile(String inputFile) {
        this.inputFile = inputFile;
    }

    public String getPathInsideArchive() {
        return pathInsideArchive;
    }

    public void setPathInsideArchive(String pathInsideArchive) {
        this.pathInsideArchive = pathInsideArchive;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    @Override
    public String toString() {
        return "ConfigLinkedDocument [name=" + name + "]";
    }
    
}
