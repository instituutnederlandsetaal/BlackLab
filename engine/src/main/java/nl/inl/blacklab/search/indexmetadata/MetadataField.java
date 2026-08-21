package nl.inl.blacklab.search.indexmetadata;

/** A metadata field. */
public interface MetadataField extends Field {

    FieldType type();

    String analyzerName();

    MetadataFieldValues values(long maxValues);

    /**
     * Wrap a cached {@link TruncatableFreqList} as a {@link MetadataFieldValues}.
     * Used to avoid re-reading the index when a suitable list is already cached.
     *
     * @param cached the cached value list
     * @return a {@link MetadataFieldValues} backed by the provided list
     */
    MetadataFieldValues valuesFromCache(TruncatableFreqList cached);

    @Override
    default String contentsFieldName() {
        return name();
    }

}
