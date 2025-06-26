package nl.inl.blacklab.tools.frequency.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Configuration for making frequency lists
 */
public class FreqListConfig {

    /**
     * A unique name that will be used as output file name
     */
    private String name = "";

    /**
     * Annotations to make frequency lists for
     */
    private List<String> annotations;

    /**
     * Metadata fields to take into account (e.g. year for frequencies per year)
     */
    private List<String> metadataFields = Collections.emptyList();

    /**
     * The size of the n-grams to use for the frequency list. Defaults to 1.
     */
    private int ngramSize = 1;

    /**
     * Cutoff configuration for the frequency list.
     * Defines a certain annotation and a minimum count.
     * If null, no cutoff is applied.
     */
    private CutoffConfig cutoff = null;

    /**
     * Lucene query to filter documents for the frequency list. Optional.
     */
    private String filter = null;

    public String getReportName() {
        return name.isEmpty() ? generateName() : name;
    }

    private String generateName() {
        List<String> parts = new ArrayList<>();
        parts.addAll(annotations);
        parts.addAll(metadataFields);
        return StringUtils.join(parts, "-");
    }

    @SuppressWarnings("unused")
    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    @SuppressWarnings("unused")
    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public List<String> getMetadataFields() {
        return metadataFields;
    }

    @SuppressWarnings("unused")
    public void setMetadataFields(List<String> metadataFields) {
        this.metadataFields = metadataFields;
    }

    public int getNgramSize() {
        return ngramSize;
    }

    @SuppressWarnings("unused")
    public void setNgramSize(int ngramSize) {
        this.ngramSize = ngramSize;
    }

    public CutoffConfig getCutoff() {
        return cutoff;
    }

    public void setCutoff(CutoffConfig cutoff) {
        this.cutoff = cutoff;
    }

    public String getFilter() {
        return filter;
    }

    @SuppressWarnings("unused")
    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public String toString() {
        return "ConfigFreqList{" +
                "name='" + name + '\'' +
                ", annotations=" + annotations +
                ", metadataFields=" + metadataFields +
                ", ngramSize=" + ngramSize +
                ", cutoff=" + cutoff +
                ", filter=" + filter +
                '}';
    }

    public String show() {
        return "- " + getReportName() + "\n" +
                "  annotations: " + annotations + "\n" +
                "  metadataFields: " + metadataFields + "\n" +
                "  ngramSize: " + ngramSize + "\n" +
                "  cutoff: " + cutoff + "\n" +
                "  filter: " + filter;

    }
}
