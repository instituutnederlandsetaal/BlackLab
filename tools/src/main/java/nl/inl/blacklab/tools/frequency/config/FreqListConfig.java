package nl.inl.blacklab.tools.frequency.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Configuration for making frequency lists
 */
public record FreqListConfig(
        String name, List<String> annotations, List<String> metadataFields, int ngramSize, CutoffConfig cutoff, String filter
) {
    public FreqListConfig {
        name = name;
        annotations = annotations == null ? Collections.emptyList() : annotations;
        metadataFields = metadataFields == null ? Collections.emptyList() : metadataFields;
        ngramSize = Math.max(ngramSize, 1); // Ensure ngramSize is at least 1
        cutoff = cutoff;
        filter = filter;
    }

    public String getReportName() {
        return name == null ? generateName() : name;
    }

    private String generateName() {
        List<String> parts = new ArrayList<>();
        parts.addAll(annotations);
        parts.addAll(metadataFields);
        return StringUtils.join(parts, "-");
    }
}
