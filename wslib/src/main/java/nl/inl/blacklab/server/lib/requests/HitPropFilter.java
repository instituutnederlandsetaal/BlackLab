package nl.inl.blacklab.server.lib.requests;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

/** A request to filter hits by a property and value */
public record HitPropFilter(HitProperty prop, PropertyValue value) {
    public static HitPropFilter fromParams(QueryParams qpar) {
        if (!StringUtils.isEmpty(qpar.getHitFilterCriterium()) && !StringUtils.isEmpty(qpar.getHitFilterValue())) {
            String hitFilterCrit = qpar.getHitFilterCriterium();
            String hitFilterVal = qpar.getHitFilterValue();
            BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
            AnnotatedField annotatedField = WebserviceParams.getAnnotatedField(index, qpar.getFieldName());
            ContextSize context = WebserviceParams.getContext(qpar.getContextParam(), qpar.config());
            HitProperty prop = HitProperty.deserialize(annotatedField, hitFilterCrit, context);
            PropertyValue value = PropertyValue.deserialize(annotatedField, hitFilterVal);
            return new HitPropFilter(prop, value);
        }
        return null;
    }
}
