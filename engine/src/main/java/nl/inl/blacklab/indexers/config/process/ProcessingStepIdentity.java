package nl.inl.blacklab.indexers.config.process;

import java.util.stream.Stream;

import nl.inl.blacklab.index.DocIndexer;

/**
 * Strip certain characters from the start and end of the value(s)
 */
public class ProcessingStepIdentity extends ProcessingStep {

    static final ProcessingStepIdentity INSTANCE = new ProcessingStepIdentity();

    private ProcessingStepIdentity() {
    }

    @Override
    public Stream<String> perform(Stream<String> values, DocIndexer docIndexer) {
        return values;
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
        return "ident()";
    }

}
