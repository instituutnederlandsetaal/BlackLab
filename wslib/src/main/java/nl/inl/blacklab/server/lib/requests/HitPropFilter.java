package nl.inl.blacklab.server.lib.requests;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

/** A request to filter hits by a property and value */
public record HitPropFilter(HitProperty prop, PropertyValue value) {
    public static HitPropFilter fromParams(QueryParams qpar) {
        if (!StringUtils.isEmpty(qpar.get(WsParam.HIT_FILTER_CRITERIUM)) && !StringUtils.isEmpty(
                qpar.get(WsParam.HIT_FILTER_VALUE))) {
            String hitFilterCrit = qpar.get(WsParam.HIT_FILTER_CRITERIUM);
            String hitFilterVal = qpar.get(WsParam.HIT_FILTER_VALUE);
            BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
            AnnotatedField annotatedField = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
            ContextSize context = ParamUtil.getContext(qpar);
            HitProperty prop = HitProperty.deserialize(annotatedField, hitFilterCrit, context);
            PropertyValue value = PropertyValue.deserialize(annotatedField, hitFilterVal);
            return new HitPropFilter(prop, value);
        }
        return null;
    }
}
