package nl.inl.blacklab.indexers.config;

import java.util.List;

import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.process.ProcessingStepIdentity;
import nl.inl.blacklab.indexers.config.process.ProcessingStepSplit;
import nl.inl.util.StringUtil;

public abstract class DocIndexerTabularBase extends DocIndexerConfig {

    protected String multipleValuesSeparatorRegex;

    public DocIndexerTabularBase(String multipleValuesSeparatorRegex) {
        super();
        this.multipleValuesSeparatorRegex = multipleValuesSeparatorRegex;
    }

    protected void indexValue(ConfigAnnotation annotation, String value) {
        // Remove unwanted unprintable characters and normalize to canonical unicode composition
        value = StringUtil.sanitizeAndNormalizeUnicode(value);
        ProcessingStep process = annotation.getProcess();
        if (process instanceof ProcessingStepIdentity || process.canProduceMultipleValues()) {
            if (process instanceof ProcessingStepIdentity) {
                // No explicit processing steps defined.
                // Perform the split processing step that is implicit for tabular formats.
                process = new ProcessingStepSplit(multipleValuesSeparatorRegex, "", "all");
            }
            // Multiple values possible.
            List<String> values = processStringMultipleValues(value, process);
            boolean first = true;
            for (String v: values) {
                annotationValueAppend(annotation.getName(), v, first ? 1 : 0);
                first = false;
            }
        } else {
            // Single value.
            value = process.performSingle(value, this);
            annotationValueAppend(annotation.getName(), value, 1);
        }
    }
}
