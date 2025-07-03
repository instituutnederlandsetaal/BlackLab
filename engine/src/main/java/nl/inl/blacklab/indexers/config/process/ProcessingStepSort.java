package nl.inl.blacklab.indexers.config.process;

import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.search.BlackLab;

/**
 * Eliminate any duplicate values.
 */
public class ProcessingStepSort extends ProcessingStep {

    @Override
    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        return values.sorted(BlackLab.defaultCollator());
    }

    @Override
    public String performSingle(String value, DocIndexer docIndexer) {
        return value;
    }

    @Override
    public boolean canProduceMultipleValues() {
        return false;
    }

    @Override
    public String toString() {
        return "sort()";
    }

}
