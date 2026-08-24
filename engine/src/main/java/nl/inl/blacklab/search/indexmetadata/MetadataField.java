package nl.inl.blacklab.search.indexmetadata;

/** A metadata field. */
public interface MetadataField extends Field {

    FieldType type();

    String analyzerName();

    MetadataFieldValues values(long maxValues);

    boolean occursInFragments();

    @Override
    default String contentsFieldName() {
        return name();
    }

}
