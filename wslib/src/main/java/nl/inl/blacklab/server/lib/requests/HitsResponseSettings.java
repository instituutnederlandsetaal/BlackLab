package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/** What should and shouldn't be included in a hits response.
 * <p>
 * Used for a regular response to a hits search and for a grouped response with hits included.
 *
 * @param omitEmptyCaptures if a capture is empty, should we omit it or not?
 * @param annotationsToWrite what annotations to include for each token position in and around the hit
 * @param metadataToWrite what metadata to include for each matched document
 */
public record HitsResponseSettings(boolean omitEmptyCaptures,
                                   Collection<Annotation> annotationsToWrite,
                                   Collection<MetadataField> metadataToWrite) {
    public static HitsResponseSettings fromParams(WebserviceParams params) {
        return new HitsResponseSettings(params.getOmitEmptyCaptures(), WebserviceOperations.getAnnotationsToWrite(params),
                WebserviceOperations.getMetadataToWrite(params));
    }
}
