package nl.inl.blacklab.indexers.config.process;

import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingStepUnique extends ProcessingStep {

    @Override
    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        return values.distinct();
    }

    public String performSingle(String value, DocIndexer docIndexer) {
        return value;
    }

    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "unique()";
    }

}
