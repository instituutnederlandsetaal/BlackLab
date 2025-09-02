package nl.inl.blacklab.tools.frequency.config.frequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.tools.frequency.config.RunConfig;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.BlackLabIndex;

/**
 * Config of a frequency list.
 *
 * @param name           name of frequency list. Used in output files.
 * @param ngramSize      ngram size.
 * @param annotatedField The annotated field to analyze for annotation frequencies.
 * @param annotations    annotations to include.
 * @param metadata       metadata to include.
 * @param filter         Lucene query to filter what documents are included.
 * @param cutoff         minimum frequency to include word.
 */
public record FrequencyListConfig(
        String name,
        Integer ngramSize,
        String annotatedField,
        List<AnnotationConfig> annotations,
        List<MetadataConfig> metadata,
        String filter,
        CutoffConfig cutoff,
        RunConfig runConfig
) {
    public FrequencyListConfig {
        if (annotatedField == null)
            annotatedField = "contents";
        if (annotations == null)
            annotations = Collections.emptyList();
        if (metadata == null)
            metadata = Collections.emptyList();
        if (name == null)
            name = generateName();
        ngramSize = Math.max(ngramSize, 1); // ngramSize is at least 1
    }

     public FrequencyListConfig changeRunConfig(final RunConfig runConfig) {
        return new FrequencyListConfig(name, ngramSize, annotatedField, annotations, metadata, filter, cutoff,
                runConfig);
    }

    /**
     * Generate a name based on annotations and metadata.
     * E.g. "word-lemma-pos-titleLevel2-witnessDate"
     */
    private String generateName() {
        final var parts = new ArrayList<>();
        parts.addAll(annotations.stream().map(AnnotationConfig::prettyName).toList());
        parts.addAll(metadata.stream().map(MetadataConfig::name).toList());
        return StringUtils.join(parts, "-");
    }

    public void verify(final BlackLabIndex index) {
        // Verify annotated field
        if (!index.annotatedFields().exists(annotatedField))
            throw new IllegalArgumentException("Annotated field not found: " + annotatedField);
        final var field = index.annotatedField(annotatedField);
        // Verify annotations
        for (final var a: annotations)
            if (!field.annotations().exists(a.name()))
                throw new IllegalArgumentException("Annotation not found: " + annotatedField + "." + a.name());
        // Verify metadata
        for (final var m: metadata)
            if (!index.metadataFields().exists(m.name()))
                throw new IllegalArgumentException("Metadata field not found: " + m.name());
        // Verify run config
        if (runConfig == null)
            throw new IllegalArgumentException("Run config missing");
    }
}
