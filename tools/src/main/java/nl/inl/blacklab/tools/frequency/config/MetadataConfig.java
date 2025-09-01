package nl.inl.blacklab.tools.frequency.config;

/**
 * Config of a metadata field in a frequency list.
 *
 * @param name       the metadata field name
 * @param required   if true, documents missing this metadata field are skipped
 * @param nullValue  if not null, this value is used when the metadata field is missing
 * @param outputAsId if true, the metadata field value is mapped to a unique integer
 */
public record MetadataConfig(String name, boolean required, String nullValue, boolean outputAsId) {
}
