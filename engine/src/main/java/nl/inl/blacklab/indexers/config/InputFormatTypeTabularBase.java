package nl.inl.blacklab.indexers.config;

import java.util.List;

import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionIdentity;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionSplit;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.util.StringUtil;
import nl.inl.util.fileprocessor.FileReference;

public abstract class InputFormatTypeTabularBase extends InputFormatTypeConfig {

    public static abstract class InputFormatTabularBase extends InputFormatConfig {

        protected String multipleValuesSeparatorRegex;

        protected InputFormatTabularBase(ConfigInputFormat config, String multipleValuesSeparatorRegex) {
            super(config);
            this.multipleValuesSeparatorRegex = multipleValuesSeparatorRegex;
        }

        protected abstract class DocTabularBase extends DocConfig {

            DocTabularBase(DocWriter docWriter, FileReference file) {
                super(docWriter, file);
            }

            protected void indexValue(ConfigAnnotation annotation, String value) {
                // Remove unwanted unprintable characters and normalize to canonical unicode composition
                value = StringUtil.sanitizeAndNormalizeUnicode(value);
                ProcessingStep process = annotation.getCompiledProcessSteps();
                if (process instanceof ProcessingInstructionIdentity || process.canProduceMultipleValues()) {
                    if (process instanceof ProcessingInstructionIdentity) {
                        // No explicit processing steps defined.
                        // Perform the split processing step that is implicit for tabular formats.
                        process = new ProcessingInstructionSplit.ProcessingStepSplit(multipleValuesSeparatorRegex, "", "all");
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
                    value = process.performSingle(value, metadataFieldValues);
                    annotationValueAppend(annotation.getName(), value, 1);
                }
            }
        }
    }
}
