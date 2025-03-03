package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for an XML element occurring in an annotated field.
 */
public class ConfigInlineTag {

    /** Configuration for extra attributes to index using XPath */
    public static class ConfigExtraAttribute {
        /** Attribute name */
        private String name;
        /** XPath to get attribute's value */
        private String valuePath;

        /** How to process annotation values (if at all) */
        private final List<ConfigProcessStep> process = new ArrayList<>();

        public ConfigExtraAttribute() {

        }

        public ConfigExtraAttribute(String name, String valuePath) {
            this.name = name;
            this.valuePath = valuePath;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValuePath() {
            return valuePath;
        }

        public void setValuePath(String valuePath) {
            this.valuePath = valuePath;
        }

        public List<ConfigProcessStep> getProcess() {
            return process;
        }

        public void setProcess(List<ConfigProcessStep> process) {
            this.process.clear();
            this.process.addAll(process);
        }

        public void validate() {
            ConfigInputFormat.req(name, "extra attribute", "name");
            ConfigInputFormat.req(valuePath, "extra attribute", "valuePath");
            for (ConfigProcessStep step : process) {
                step.validate();
            }
        }
    }

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

    /** Extra attributes to index with the tag via an XPath expression */
    private List<ConfigExtraAttribute> extraAttributes = Collections.emptyList();

    public ConfigInlineTag() {
    }

    public ConfigInlineTag(String path, String displayAs) {
        setPath(path);
        setDisplayAs(displayAs);
    }

    public void validate() {
        ConfigInputFormat.req(path, "inline tag", "path");
        for (ConfigExtraAttribute ea : extraAttributes) {
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

    public void setExcludeAttributes(List<String> exclAttr) {
        this.excludeAttributes = new HashSet<>(exclAttr);
    }

    public Set<String> getExcludeAttributes() {
        return excludeAttributes;
    }

    public void setIncludeAttributes(List<String> includeAttributes) {
        this.includeAttributes = includeAttributes;
    }

    public List<String> getIncludeAttributes() {
        return includeAttributes;
    }

    public void setExtraAttributes(List<ConfigExtraAttribute> extraAttributes) {
        this.extraAttributes = extraAttributes;
    }

    public List<ConfigExtraAttribute> getExtraAttributes() {
        return extraAttributes;
    }

    public boolean hasDetailedAttributeConfig() {
        return includeAttributes != null || !excludeAttributes.isEmpty() || !extraAttributes.isEmpty();
    }
}
