package org.ivdnt.blacklab.proxy.backend.blacklab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ivdnt.blacklab.proxy.representation.AnnotatedField;
import org.ivdnt.blacklab.proxy.representation.Annotation;
import org.ivdnt.blacklab.proxy.representation.Corpus;
import org.ivdnt.blacklab.proxy.representation.MetadataField;
import org.ivdnt.blacklab.proxy.representation.MetadataFieldGroup;
import org.ivdnt.blacklab.proxy.representation.SpecialFieldInfo;
import org.ivdnt.blacklab.proxy.representation.VersionInfo;

import nl.inl.blacklab.search.indexmetadata.CustomProps;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.results.ResultAnnotatedField;
import nl.inl.blacklab.server.lib.results.ResultAnnotationInfo;
import nl.inl.blacklab.server.lib.results.ResultCorpusInfo;
import nl.inl.blacklab.server.lib.results.ResultMetadataField;

/** Converts BlackLab objects to equivalent BLS response objects.
 *
 * BlackLab objects already have their own serialization for the indexmetadata that is stored in the index,
 * so we need separate response objects for "new-BLS".
 */
public class ResponseConverter {

    private boolean isNewApi;

    public ResponseConverter(boolean isNewApi) {
        this.isNewApi = isNewApi;
    }

    public AnnotatedField annotatedField(ResultAnnotatedField raf, boolean includeCustom) {
        nl.inl.blacklab.search.indexmetadata.AnnotatedField in = raf.getFieldDesc();
        AnnotatedField out = new AnnotatedField(in.name());

        // TODO custom (new API)
        CustomProps custom = in.custom();
        if (isNewApi) {
            if (includeCustom)
                out.custom = custom.asMap();
        } else {
            out.displayName = custom.get("displayName", "");
            out.description = custom.get("description", "");
            out.displayOrder = custom.get("displayOrder", List.of());
        }

        out.annotations = raf.getAnnotInfos().values().stream()
                .map(a -> annotation(a, includeCustom)).toList();
        out.hasContentStore = in.hasContentStore();
        out.hasXmlTags = in.hasRelationAnnotation();
        out.indexName = in.index().name();
        out.mainAnnotation = in.mainAnnotation().name();
        //TODO out.documentCount
        //TODO out.tokenCount
        return out;
    }

    public Annotation annotation(ResultAnnotationInfo rai, boolean includeCustom) {
        nl.inl.blacklab.search.indexmetadata.Annotation in = rai.getAnnotation();
        Annotation out = new Annotation();
        out.name = in.name();

        CustomProps custom = in.custom();
        if (isNewApi) {
            if (includeCustom)
                out.custom = custom.asMap();
        } else {
            out.displayName = custom.get("displayName", "");
            out.description = custom.get("description", "");
            out.uiType = custom.get("uiType", "");
        }

        out.hasForwardIndex = in.hasForwardIndex();
        out.isInternal = in.isInternal();
        out.offsetsAlternative = in.offsetsSensitivity() == null ? "" :
                in.offsetsSensitivity().sensitivity().luceneFieldSuffix();
        out.parentAnnotation = in.isSubannotation() ? in.parentAnnotation().name() : null;
        out.sensitivity = in.sensitivitySetting() == null ? "" : in.sensitivitySetting().stringValueForResponse();
        out.subannotations = new ArrayList<>(in.subannotationNames());
        if (rai.isShowValues()) {
            out.terms = rai.getTerms().getValues();
            if (!isNewApi)
                out.values = new ArrayList<>(rai.getTerms().getValues().keySet());
            out.valueListComplete = !rai.getTerms().isTruncated();
        }
        return out;
    }

    public SpecialFieldInfo specialFieldInfo(IndexMetadata metadata) {
        CustomProps custom = metadata.custom();
        SpecialFieldInfo sfi = new SpecialFieldInfo(
                metadata.metadataFields().pidField().name(), custom.get("titleField", ""));
        sfi.authorField = custom.get("authorField", "");
        sfi.dateField = custom.get("dateField", "");
        return sfi;
    }

    public List<AnnotatedField> annotatedFields(List<ResultAnnotatedField> annotatedFields, boolean includeCustom) {
        return annotatedFields.stream().map(a -> annotatedField(a, includeCustom)).toList();
    }

    public List<MetadataField> metadataFields(List<ResultMetadataField> metadataFields, boolean includeCustom) {
        return metadataFields.stream().map(mf -> metadataField(mf, includeCustom)).toList();
    }

    private MetadataField metadataField(ResultMetadataField mf, boolean includeCustom) {
        nl.inl.blacklab.search.indexmetadata.MetadataField in = mf.getFieldDesc();
        MetadataField out = new MetadataField();
        out.name = in.name();
        out.fieldName = in.name();
        CustomProps custom = in.custom();
        if (isNewApi) {
            if (includeCustom)
                out.custom = custom.asMap();
        } else {
            out.displayName = custom.get("displayName", "");
            out.description = custom.get("description", "");
            out.uiType = custom.get("uiType", "");
            out.unknownCondition = custom.get("unknownCondition", "");
            out.unknownValue = custom.get("unknownValue", "");
            out.displayValues = custom.get("displayValues", Map.of());
        }
        out.type = in.type().stringValue();
        out.analyzer = in.analyzerName();
        out.fieldValues = mf.getFieldValues();
        out.valueListComplete = mf.isValueListComplete();
        return out;
    }

    public MetadataFieldGroup metadataFieldGroup(Map.Entry<String, List<String>> metadataFieldGroupEntry) {
        MetadataFieldGroup group = new MetadataFieldGroup();
        group.name = metadataFieldGroupEntry.getKey();
        group.fields = new ArrayList<>(metadataFieldGroupEntry.getValue());
        return group;
    }

    public Corpus corpus(RequestCorpusInfo req, ResultCorpusInfo ci) {
        IndexMetadata metadata = ci.getMetadata();
        SpecialFieldInfo specialFields = isNewApi ? null : specialFieldInfo(metadata);
        Corpus corpus = new Corpus(req.corpusName(), specialFields,
                annotatedFields(ci.getAnnotatedFields(), req.customInfo()),
                metadataFields(ci.getMetadataFields(), req.customInfo()));

        CustomProps custom = metadata.custom();
        if (isNewApi) {
            corpus.pidField = metadata.metadataFields().pidField().name();
            if (req.customInfo())
                corpus.custom = custom.asMap();
        } else {
            corpus.displayName = custom.get("displayName", "");
            corpus.description = custom.get("description", "");
        }

        //TODO corpus.annotationGroups = ci.
        corpus.metadataFieldGroups = ci.getMetadataFieldGroups().entrySet().stream().map(this::metadataFieldGroup).toList();
        corpus.contentViewable = ci.getMetadata().contentViewable();
        corpus.documentCount = (long) ci.getMetadata().documentCount();
        corpus.tokenCount = ci.getMetadata().tokenCount();
        // TODO: counts per field
        corpus.documentFormat = ci.getMetadata().documentFormat();
        corpus.status = ci.getProgress().getIndexStatus().toString();
        corpus.textDirection = ci.getMetadata().custom().get("textDirection", "ltr");
        corpus.mainAnnotatedField = ci.getMainAnnotatedField();
        corpus.versionInfo = versionInfo(metadata);
        return corpus;
    }

    private static VersionInfo versionInfo(IndexMetadata metadata) {
        VersionInfo v = new VersionInfo();
        v.blacklabBuildTime = metadata.indexBlackLabBuildTime();
        v.blacklabVersion = metadata.indexBlackLabVersion();
        v.blacklabScmRevision = metadata.indexBlackLabScmRevision();
        v.indexFormat = metadata.indexFormat();
        v.timeCreated = metadata.timeCreated();
        v.timeModified = metadata.timeModified();
        return v;
    }
}
