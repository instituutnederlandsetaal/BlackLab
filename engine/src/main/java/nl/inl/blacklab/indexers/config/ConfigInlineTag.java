package nl.inl.blacklab.indexers.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;

/**
 * Configuration for an XML element occurring in an annotated field.
 */
public class ConfigInlineTag {

    /** XPath to the inline tag, relative to the container element */
    private String path;

    /**
     * (optional) How to display this inline tag when viewing document in the
     * frontend (used as CSS class for this inline tag in generated XSLT; there are
     * several predefined classes such as sentence, paragraph, line-beginning,
     * page-beginning)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String displayAs = "";

    /**
     * XPath to resolve and remember the start positions for,
     * so we can refer to them from standoff annotations.
     * (Used for tei:anchor, so end position is not used)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String tokenIdPath = null;

    /** Extra attributes to index with the tag via an XPath expression,
     *  as well as tag attributes to include/exclude (optionally with processing steps). */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, ConfigAttribute> attributes = Collections.emptyMap();

    /** Should we index all attributes on the tag by default,
     *  or only those explicitly mentioned? */
    @JsonIgnore
    private boolean defaultIndexAttributes = true;

    public ConfigInlineTag() {
    }

    public ConfigInlineTag(String path, String displayAs) {
        setPath(path);
        setDisplayAs(displayAs);
    }

    void validate(InputFormatMessages messages) {
        messages.mustHave("inline tag", path, "path");
        for (ConfigAttribute ea: attributes.values()) {
            ea.validate(messages);
        }
    }

    public ConfigInlineTag copy() {
        return new ConfigInlineTag(path, displayAs);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDisplayAs() {
        return displayAs;
    }

    public void setDisplayAs(String displayAs) {
        this.displayAs = displayAs;
    }

    public String getTokenIdPath() {
        return tokenIdPath;
    }

    public void setTokenIdPath(String tokenIdPath) {
        this.tokenIdPath = tokenIdPath;
    }

    @Override
    public String toString() {
        return "ConfigInlineTag [displayAs=" + displayAs + "]";
    }

    public void setAttributes(List<ConfigAttribute> attributes) {
        // Is there a "default exclude" rule?
        Optional<ConfigAttribute> ex = attributes.stream().filter(ConfigAttribute::isDefaultExclude).findFirst();
        if (ex.isPresent())
            this.defaultIndexAttributes = false;
        // Filter out the default exclude rule
        this.attributes = new LinkedHashMap<>();
        attributes.stream()
                .filter(a -> !a.isDefaultExclude())
                .forEach(a -> {
                    if (this.attributes.containsKey(a.getName()))
                        throw new InvalidInputFormatConfig("Duplicate attribute name: " + a.getName());
                    this.attributes.put(a.getName(), a);
                });
    }

    public boolean isDefaultIndexAttributes() {
        return defaultIndexAttributes;
    }

    public Map<String, ConfigAttribute> getAttributes() {
        // We don't synchronize reads, as attributes is only set once when config is read
        return attributes;
    }

    public void setIncludeAttributes(Object v) {
        throw new InvalidInputFormatConfig("includeAttributes no longer allowed in .blf.yaml (use 'attributes' instead)");
    }

    public void setExcludeAttributes(Object v) {
        throw new InvalidInputFormatConfig("excludeAttributes no longer allowed in .blf.yaml (use 'attributes' with exclude: true instead)");
    }

    public void setExtraAttributes(Object v) {
        throw new InvalidInputFormatConfig("excludeAttributes no longer allowed in .blf.yaml (use 'attributes' instead)");
    }
}
