package nl.inl.blacklab.indexers.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private String displayAs = "";

    /**
     * XPath to resolve and remember the start positions for,
     * so we can refer to them from standoff annotations.
     * (Used for tei:anchor, so end position is not used)
     */
    private String tokenIdPath = null;

    /** Don't index attributes in this list (unless includeAttributes is set). */
    private Set<String> excludeAttributes = Collections.emptySet();

    /** If set: ignore excludeAttributes and don't index attributes not in this list. */
    private List<String> includeAttributes = null;

    /** Extra attributes to index with the tag via an XPath expression,
     *  as well as tag attributes to include (optionally with processing steps). */
    private List<ConfigAttribute> attributes = Collections.emptyList();

    /** Should we index all attributes on the tag by default,
     *  or only those explicitly mentioned? */
    private boolean defaultIndexAttributes = true;

    /** The final attribute specification which combines include, exclude and extra. */
    private Map<String, ConfigAttribute> allAttributes;

    public ConfigInlineTag() {
    }

    public ConfigInlineTag(String path, String displayAs) {
        setPath(path);
        setDisplayAs(displayAs);
    }

    public void validate() {
        ConfigInputFormat.req(path, "inline tag", "path");
        for (ConfigAttribute ea : attributes) {
            ea.validate();
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

    public synchronized void setExcludeAttributes(List<String> exclAttr) {
        this.excludeAttributes = new HashSet<>(exclAttr);
        allAttributes = null;
    }

    public synchronized void setIncludeAttributes(List<String> includeAttributes) {
        if (!includeAttributes.isEmpty())
            this.defaultIndexAttributes = false; // we're explicitly setting the attributes to include
        this.includeAttributes = includeAttributes;
        allAttributes = null;
    }

    public synchronized void setAttributes(List<ConfigAttribute> attributes) {
        // Is there a "default exclude" rule?
        Optional<ConfigAttribute> ex = attributes.stream().filter(ConfigAttribute::isDefaultExclude).findFirst();
        if (ex.isPresent())
            this.defaultIndexAttributes = false;
        // Filter out the default exclude rule
        this.attributes = attributes.stream().filter(a -> !a.isDefaultExclude()).toList();
        allAttributes = null;
    }

    public boolean isDefaultIndexAttributes() {
        return defaultIndexAttributes;
    }

    public synchronized Map<String, ConfigAttribute> getAttributes() {
        if (allAttributes == null) {
            allAttributes = new LinkedHashMap<>();
            if (!excludeAttributes.isEmpty()) {
                for (String name: excludeAttributes) {
                    ConfigAttribute ca = new ConfigAttribute();
                    ca.setName(name);
                    ca.setExclude(true);
                    allAttributes.put(ca.getName(), ca);
                }
            }
            if (includeAttributes != null) {
                for (String name: includeAttributes) {
                    if (allAttributes.containsKey(name))
                        throw new InvalidInputFormatConfig("Duplicate attribute name: " + name);
                    ConfigAttribute ca = new ConfigAttribute();
                    ca.setName(name);
                    allAttributes.put(ca.getName(), ca);
                }
            }
            for (ConfigAttribute attr: attributes) {
                if (allAttributes.containsKey(attr.getName()))
                    throw new InvalidInputFormatConfig("Duplicate attribute name: " + attr.getName());
                allAttributes.put(attr.getName(), attr);
            }
        }
        return allAttributes;
    }
}
