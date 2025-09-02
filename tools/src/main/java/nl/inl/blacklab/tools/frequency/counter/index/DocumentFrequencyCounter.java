package nl.inl.blacklab.tools.frequency.counter.index;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.lucene.document.Document;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.frequency.MetadataConfig;
import nl.inl.blacklab.tools.frequency.data.GroupId;
import nl.inl.blacklab.tools.frequency.data.document.DocumentMetadata;
import nl.inl.blacklab.tools.frequency.data.document.DocumentTokens;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;

/**
 * Counts frequencies in a single document.
 */
final public class DocumentFrequencyCounter {
    private static final int[] EMPTY_ARRAY = new int[0];
    private final FrequencyListConfig cfg;
    private final IndexHelper helper;
    private final Document doc;
    private final int docId;
    private final int docLength;

    DocumentFrequencyCounter(final int docId, final BlackLabIndex index, final FrequencyListConfig cfg,
            final IndexHelper helper) throws IOException {
        this.cfg = cfg;
        this.helper = helper;
        this.docId = docId;
        /*
         * Document properties that are used in the grouping. (e.g. for query "all tokens, grouped by lemma + document year", will contain DocProperty("document year")
         * This is not necessarily limited to just metadata, can also contain any other DocProperties such as document ID, document length, etc.
         */
        // Token properties that need to be grouped on, with sensitivity (case-sensitive grouping or not) and Terms
        final var metaFieldNames = cfg.metadata().stream().map(MetadataConfig::name).toList();

        // Start actually calculating the requests frequencies.

        // We do have hit properties, so we need to use both document metadata and the tokens from the forward index to
        // calculate the frequencies.
        final String fieldName = cfg.annotatedField();
        final String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);

        // Determine all the fields we want to be able to load, so we don't need to load the entire document
        final Set<String> fieldsToLoad = new HashSet<>();
        fieldsToLoad.add(lengthTokensFieldName);
        fieldsToLoad.addAll(metaFieldNames);

        final var reader = index.reader();
        this.doc = reader.document(docId, fieldsToLoad);
        this.docLength =
                Integer.parseInt(doc.get(lengthTokensFieldName)) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
    }

    /**
     * Merge occurrences in this doc with global occurrences.
     */
    private static void mergeOccurrences(final Map<GroupId, Integer> global, final Map<GroupId, Integer> doc) {
        doc.forEach((groupId, docCount) -> {
            global.merge(groupId, docCount, Integer::sum);
        });
    }

    public void process(final Map<GroupId, Integer> occurrences) {
        // Step 1: read all values for the to-be-grouped annotations for this document
        // This will create one int[] for every annotation, containing ids that map to the values for this document for this annotation
        final DocumentTokens doc = getDocumentTokens();
        // Step 2: retrieve the to-be-grouped metadata for this document
        final DocumentMetadata meta = getDocumentMetadata();
        if (meta == null) {
            // Skip this document, it has no valid metadata values
            return;
        }
        // now we have all values for all relevant annotations for this document
        // iterate again and pair up the nth entries for all annotations, then store that as a group.
        final var occsInDoc = getDocumentFrequencies(doc, meta);
        // Step 3: merge the occurrences in this document with the global occurrences
        mergeOccurrences(occurrences, occsInDoc);
    }

    private DocumentTokens getDocumentTokens() {
        final int numAnnotations = helper.annotations().annotations().size();
        final var tokens = new int[numAnnotations][];
        final var sorting = new int[numAnnotations][];

        int i = 0;
        for (final var fi: helper.annotations().forwardIndices()) {
            // Get the tokens for this annotation
            tokens[i] = fi.getDocument(docId);
            // And look up their sort values
            sorting[i] = new int[docLength + 1]; // +1 for the extra closing token
            fi.terms().toSortOrder(tokens[i], sorting[i], MatchSensitivity.INSENSITIVE);
            i++;
        }

        return new DocumentTokens(tokens, sorting);
    }

    @Nullable
    private DocumentMetadata getDocumentMetadata() {
        final int numFields = cfg.metadata().size();
        final var metaValues = new int[numFields];
        // for each metadata field defined in the config
        for (int i = 0; i < numFields; i++) {
            final MetadataConfig metaCfg = cfg.metadata().get(i);
            // get its value
            String fieldValue = doc.get(metaCfg.name());
            // and if it's null
            if (fieldValue == null || fieldValue.isEmpty()) {
                // optionally replace it with a default value
                if (metaCfg.nullValue() != null) {
                    fieldValue = metaCfg.nullValue();
                } else if (metaCfg.required()) {
                    // if it's required but not preset, discard this document
                    return null;
                }
            }
            // retrieve the id for this value
            final int id = helper.database().freqMetadata().getIdx(metaCfg.name(), fieldValue);
            // add the processed value
            metaValues[i] = id;
        }
        // precompute, it's the same for all hits in document
        final int hash = Arrays.hashCode(metaValues);
        return new DocumentMetadata(metaValues, hash);
    }

    private Map<GroupId, Integer> getDocumentFrequencies(final DocumentTokens doc, final DocumentMetadata meta) {
        // Keep track of term occurrences in this document; later we'll merge it with the global term frequencies
        final Map<GroupId, Integer> occsInDoc = new Object2IntOpenHashMap<>();
        final int ngramSize = cfg.ngramSize();
        final int numAnnotations = helper.annotations().annotations().size();

        if (numAnnotations == 0) {
            // just doc length, no annotations
            final var groupId = new GroupId(EMPTY_ARRAY, EMPTY_ARRAY, meta);
            // Count occurrence in this doc
            final int tokenCount = docLength - (ngramSize - 1);
            // If we already have this group, increment the count
            occsInDoc.merge(groupId, tokenCount, Integer::sum);
            return occsInDoc; // no annotations, so no ngrams to calculate
        }

        // We can't get an ngram for the last ngramSize-1 tokens
        for (int tokenIndex = 0; tokenIndex < docLength - (ngramSize - 1); ++tokenIndex) {
            final int[] tokenIds = new int[numAnnotations * ngramSize];
            final int[] sortPositions = new int[numAnnotations * ngramSize];

            // Unfortunate fact: token ids are case-sensitive, and in order to group on a token's values case and diacritics insensitively,
            // we need to actually group by their "sort positions" - which is just the index the term would have if all terms would have been sorted
            // so in essence it's also an "id", but a case-insensitive one.
            // we could further optimize to not do this step when grouping sensitively by making a specialized instance of the GroupIdHash class
            // that hashes the token ids instead of the sortpositions in that case.
            for (int annotationIndex = 0, arrIndex = 0;
                 annotationIndex < numAnnotations; ++annotationIndex, arrIndex += ngramSize) {
                // get array slices of ngramSize
                final int[] tokenValues = doc.tokens()[annotationIndex];
                System.arraycopy(tokenValues, tokenIndex, tokenIds, arrIndex, ngramSize);
                final int[] sortValues = doc.sorting()[annotationIndex];
                System.arraycopy(sortValues, tokenIndex, sortPositions, arrIndex, ngramSize);
            }
            final var groupId = new GroupId(tokenIds, sortPositions, meta);

            // Only add if it is above the cutoff
            if (cfg.cutoff() != null) {
                // Check if any of the ngrams tokens is below the cutoff
                boolean skipGroup = false;
                for (int j = 0; j < ngramSize; j++) {
                    final int tokenID = groupId.tokens()[j];
                    final String token = helper.cutoff().terms().get(tokenID);
                    if (!helper.cutoff().aboveCutoff().contains(token)) {
                        skipGroup = true;
                        break; // no need to check the rest of the ngram
                    }
                }
                if (skipGroup)
                    continue; // skip this group, it is below the cutoff
            }

            // Count occurrence in this doc
            occsInDoc.merge(groupId, 1, Integer::sum);
        }
        return occsInDoc;
    }
}
