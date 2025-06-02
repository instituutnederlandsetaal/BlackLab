package nl.inl.blacklab.search.indexmetadata;

import java.util.stream.Stream;

/**
 * A named, ordered list of metadata fields.
 * 
 * Used to divide metadata into logical groups.
 */
public interface MetadataFieldGroup extends Iterable<String> {

    String name();

    Stream<String> stream();

    boolean addRemainingFields();
}
