package nl.inl.blacklab.server.lib.requests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFields;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WriteCsv;

/** What should and shouldn't be included in a hits response.
 * <p>
 * Used for a regular response to a hits search and for a grouped response with hits included.
 *
 * @param omitEmptyCaptures if a capture is empty, should we omit it or not?
 * @param annotationsToInclude what annotations to include for each token position in and around the hit
 */
public record HitsResponseSettings(boolean omitEmptyCaptures,
                                   List<Annotation> annotationsToInclude,
                                   List<WriteCsv.SpanAndAttributeName> spanAttributes) {
    public static HitsResponseSettings fromParams(QueryParams qpar) {
        List<WriteCsv.SpanAndAttributeName> andAttributes = qpar.getListSpanAttributes().stream()
                .map(WriteCsv.SpanAndAttributeName::fromString).toList();
        boolean omitEmptyCaptures = qpar.optOmitEmptyCaptures()
                .orElse(qpar.config().getParameters().isOmitEmptyCaptures());
        return new HitsResponseSettings(
                omitEmptyCaptures,
                getAnnotationsToWrite(qpar),
                andAttributes
        );
    }

    /**
     * Returns the annotations to write out.
     * <p>
     * By default, all annotations are returned.
     * Annotations are returned in requested order, or in their definition/display order.
     *
     * @return the annotations to write out, as specified by the (optional) "listvalues" query parameter.
     */
    public static List<Annotation> getAnnotationsToWrite(QueryParams qpar) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        AnnotatedFields fields = index.annotatedFields();
        Collection<String> requestedAnnotations = qpar.getListValuesFor();
        boolean all = false;
        if (requestedAnnotations.contains("*")) {
            all = true;
        }
        // NOTE: we use all fields to make sure this works for parallel corpora too!
        //       obviously only annotations that are actually from the field(s) searched will be included in the output.
        List<Annotation> ret = new ArrayList<>();
        for (AnnotatedField f : fields) {
            for (Annotation a : f.annotations()) {
                if (all || requestedAnnotations.isEmpty() || requestedAnnotations.contains(a.name())) {
                    ret.add(a);
                }
            }
        }
        return ret;
    }
}
