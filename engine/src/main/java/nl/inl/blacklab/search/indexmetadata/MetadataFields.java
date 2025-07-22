package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Metadata fields in an index. */
public interface MetadataFields extends Iterable<MetadataField> {

    public static final String SPECIAL_FIELD_SETTING_PID = "pidField";

	/**
	 * Name of the default analyzer to use for metadata fields.
	 * @return the analyzer name (or DEFAULT for the BlackLab default)
	 */
	String defaultAnalyzerName();

	Stream<MetadataField> stream();

	/**
	 * Get the specified metadata field config.
	 *
	 * @param fieldName metadata field name
	 * @return metadata field config
	 * @throws IllegalArgumentException if field not found
	 */
	MetadataField get(String fieldName);

    Map<String, ? extends MetadataFieldGroup> groups();

    MetadataField pidField();

    /**
	 * Does the specified field exist?
	 * 
	 * @return true if it exists, false if not
	 */
    boolean exists(String name);

    List<String> names();

}
