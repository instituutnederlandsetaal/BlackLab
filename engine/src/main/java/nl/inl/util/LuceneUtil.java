package nl.inl.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFields;

public final class LuceneUtil {

    private static final Logger logger = LogManager.getLogger(LuceneUtil.class);

    private static final Charset LUCENE_DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private LuceneUtil() {}

    /**
     * Query parser that will correctly produce numeric range queries for numeric fields.
     */
    private static class FieldTypeAwareQueryParser extends QueryParser {
        private final MetadataFields metadataFields;

        public FieldTypeAwareQueryParser(MetadataFields metadataFields, String defaultField, Analyzer analyzer) {
            super(defaultField, analyzer);
            this.metadataFields = metadataFields;
        }

        private boolean isNumericField(String fieldName) {
            MetadataField f = metadataFields == null ? null : metadataFields.get(fieldName);
            return f != null && f.type() == FieldType.NUMERIC;
        }

        @Override
        protected Query newTermQuery(Term term, float boost) {
            if (isNumericField(term.field())) {
                int v = Integer.parseInt(term.text());
                return IntPoint.newRangeQuery(term.field(), v, v);
            } else {
                return super.newTermQuery(term, boost);
            }
        }

        @Override
        protected Query newRangeQuery(String field, String startValue, String endValue, boolean startInclusive,
                boolean endInclusive) {
            if (isNumericField(field)) {
                if (!startInclusive || !endInclusive)
                    throw new InvalidQuery("Numeric range queries must be inclusive");
                int lowerValue = Integer.parseInt(startValue);
                int upperValue = Integer.parseInt(endValue);
                return IntPoint.newRangeQuery(field, lowerValue, upperValue);
            } else {
                return super.newRangeQuery(field, startValue, endValue, startInclusive, endInclusive);
            }
        }
    }

    /**
     * Parse a query in the Lucene query language format (QueryParser supplied with
     * Lucene).
     *
     * @param index our index, so we know the field types, or null to always produce term queries
     * @param luceneQuery the query string
     * @param analyzer analyzer to use
     * @param defaultField default search field
     * @return the query
     * @throws ParseException on syntax error
     */
    public static Query parseLuceneQuery(BlackLabIndex index, String luceneQuery, Analyzer analyzer, String defaultField)
            throws ParseException {
        MetadataFields metadataFields = index == null ? null : index.metadataFields();
        QueryParser qp = new FieldTypeAwareQueryParser(metadataFields, defaultField, analyzer);
        qp.setAllowLeadingWildcard(true);
        return qp.parse(luceneQuery);
    }

    /**
     * Get all words between the specified start and end positions from the term
     * vector.
     *
     * NOTE: this may return an array of less than the size requested, if the
     * document ends before the requested end position.
     *
     * @param reader the index
     * @param doc doc id
     * @param luceneName the index field from which to use the term vector
     * @param start start position (first word we want to request)
     * @param end end position (last word we want to request)
     * @param partialOk is it okay if we're missing words in the middle, or do we
     *            need them all? (debug)
     * @return the words found, in order
     */
    public static String[] getWordsFromTermVector(IndexReader reader, int doc,
            String luceneName, int start, int end, boolean partialOk) {

        // Retrieve the term position vector of the contents of this document.
        // NOTE: might be faster to retrieve all term vectors at once

        try {
            Terms terms = reader.getTermVector(doc, luceneName);
            if (terms == null) {
                throw new IllegalArgumentException("Field " + luceneName + " has no Terms");
            }
            if (!terms.hasPositions())
                throw new IllegalArgumentException("Field " + luceneName + " has no character postion information");
            TermsEnum termsEnum = terms.iterator();

            // Verzamel concordantiewoorden uit term vector
            PostingsEnum docPosEnum = null;
            int numFound = 0;
            String[] concordanceWords = new String[end - start + 1];
            while (termsEnum.next() != null) {
                docPosEnum = termsEnum.postings(docPosEnum, PostingsEnum.POSITIONS);
                while (docPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    for (int i = 0; i < docPosEnum.freq(); i++) {
                        int position = docPosEnum.nextPosition();
                        if (position == -1)
                            throw new InvalidIndex("Unexpected missing position (i=" + i + ", docPosEnum.freq() = "
                                    + docPosEnum.freq() + ")");
                        if (position >= start && position <= end) {
                            if (concordanceWords[position - start] == null)
                                concordanceWords[position - start] = termsEnum.term().utf8ToString();
                            else
                                concordanceWords[position - start] += "|" + termsEnum.term().utf8ToString();
                            numFound++;
                        }
                    }
                    if (numFound == concordanceWords.length)
                        return concordanceWords;
                }
            }

            if (numFound < concordanceWords.length && !partialOk) {
                // If we simply ran into the end of the document, that's okay;
                // but if words are missing in the middle, that's not.
                String[] partial = new String[numFound];
                System.arraycopy(concordanceWords, 0, partial, 0, numFound);
                for (int i = 0; i < numFound; i++) {
                    if (partial[i] == null) {
                        throw new InvalidIndex("Not all words found (" + numFound + " out of "
                                + concordanceWords.length
                                + "); missing words in the middle of concordance!");
                    }
                }
                return partial;
            }
            return concordanceWords;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Check if a Lucene field has offsets stored.
     *
     * @param reader our index
     * @param luceneFieldName field to check
     * @return true iff field has offsets
     */
    public static boolean hasOffsets(IndexReader reader, String luceneFieldName) {
        // Iterate over documents in the index until we find a annotation
        // for this annotated field that has stored character offsets. This is
        // our main annotation.

        // Note that we can't simply retrieve the field from a document and
        // check the FieldType to see if it has offsets or not, as that information
        // is incorrect at search time (always set to false, even if it has offsets).

        Bits liveDocs = MultiBits.getLiveDocs(reader);
        for (int n = 0; n < reader.maxDoc(); n++) {
            if (liveDocs == null || liveDocs.get(n)) {
                try {
                    Terms terms = reader.getTermVector(n, luceneFieldName);
                    if (terms == null) {
                        // No term vector; probably not stored in this document.
                        continue;
                    }
                    if (terms.hasOffsets()) {
                        // This field has offsets stored. Must be the main alternative.
                        return true;
                    }
                    // This alternative has no offsets stored. Don't look at any more
                    // documents, go to the next alternative.
                    break;
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }
        }
        return false;
    }

    /** Handle a term. */
    @FunctionalInterface
    public interface TermHandler {
    	/** Handle a term.
    	 * 
    	 * @param term term to handle
         * @param freq total term frequency
    	 * @return whether or not to continue iterating over terms.
    	 */
    	boolean term(String term, long freq);
    }
    
    /**
     * Find terms in the index based on a prefix. Useful for autocomplete.
     *
     * Note that this method iterates over parts of the index sequentially, so a
     * term may be reported multiple times. The frequencies should be summed if you
     * want the total frequency.
     *
     * @param index the index
     * @param fieldName the field to find terms for
     * @param startFrom (prefix of a) term to start iterating from, or null to start at the beginning
     * @param handler called to handle terms found, until it returns false (or we run out of terms)
     */
    public static void getFieldTerms(IndexReader index, String fieldName, String startFrom, TermHandler handler) {
    	boolean allTerms = startFrom == null || startFrom.isEmpty();
        if (allTerms) {
        	startFrom = "";
        }
        try {
            outerLoop:
            for (LeafReaderContext leafReader : index.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms == null) {
                    if (logger.isDebugEnabled())
                        logger.debug("no terms for field " + fieldName + " in leafReader, skipping");
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();
                BytesRef brPrefix = new BytesRef(startFrom.getBytes(LUCENE_DEFAULT_CHARSET));
                TermsEnum.SeekStatus seekStatus = termsEnum.seekCeil(brPrefix);

                if (seekStatus == TermsEnum.SeekStatus.END) {
                    continue;
                }
                for (BytesRef term = termsEnum.term(); term != null; term = termsEnum.next()) {
                    String termText = term.utf8ToString();
                    if (!handler.term(termText, termsEnum.totalTermFreq()))
                    	break outerLoop;
                }
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Find terms in the index based on a prefix. Useful for autocomplete.
     *
     * @param index the index
     * @param fieldName the field
     * @param prefix the prefix we're looking for (null or empty string for all
     *            terms)
     * @param sensitive match case-sensitively or not?
     * @param maxResults max. number of results to return (or -1 for all)
     * @return the matching terms
     */
    public static List<String> findTermsByPrefix(IndexReader index, String fieldName,
            String prefix, boolean sensitive, long maxResults) {
        boolean allTerms = prefix == null || prefix.isEmpty();
        if (allTerms) {
            prefix = "";
            sensitive = true; // don't do unnecessary work in this case
        }
        try {
            if (!sensitive)
                prefix = StringUtil.stripAccents(prefix).toLowerCase();
            Set<String> results = new TreeSet<>();
            
            outerLoop:
            for (LeafReaderContext leafReader : index.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms == null) {
                    if (logger.isDebugEnabled())
                        logger.debug("no terms for field " + fieldName + " in leafReader, skipping");
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();
                BytesRef brPrefix = new BytesRef(prefix.getBytes(LUCENE_DEFAULT_CHARSET));
                TermsEnum.SeekStatus seekStatus = termsEnum.seekCeil(brPrefix);

                if (seekStatus == TermsEnum.SeekStatus.END) {
                    continue;
                }
                for (BytesRef term = termsEnum.term(); term != null; term = termsEnum.next()) {
                    if (maxResults > 0 && results.size() > maxResults)
                        break outerLoop;
                    
                    String termText = term.utf8ToString();
                    boolean startsWithPrefix = allTerms || (sensitive ? StringUtil.stripAccents(termText).startsWith(prefix)
                            : termText.startsWith(prefix));
                    if (!startsWithPrefix) {
                        // Doesn't match prefix or different field; no more matches
                        break;
                    }
                    // Match, add term
                    results.add(termText);
                }
            }
            
            return new ArrayList<>(results);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Get term frequencies for an annotation in a subset of documents.
     *
     * @param documentFilterQuery document filter, or null for all documents
     * @param annotSensitivity field to get frequencies for
     * @param searchTerms list of terms to get frequencies for, or null for all terms
     * @return term frequencies
     */
    public static Map<String, Integer> termFrequencies(IndexSearcher indexSearcher, Query documentFilterQuery,
            AnnotationSensitivity annotSensitivity, Set<String> searchTerms) {
        try {
            Map<String, Integer> freq = new HashMap<>();
            IndexReader indexReader = indexSearcher.getIndexReader();
            String field = annotSensitivity.luceneField();

            Weight weight = null;
            if (documentFilterQuery != null) {
                weight = indexSearcher.createWeight(documentFilterQuery,ScoreMode.COMPLETE_NO_SCORES,1.0f);
                if (weight == null)
                    throw new InvalidIndex("weight == null");
            }

            for (LeafReaderContext arc : indexReader.leaves()) {
                if (arc == null)
                    throw new InvalidIndex("arc == null");
                if (arc.reader() == null)
                    throw new InvalidIndex("arc.reader() == null");

                LeafReader reader = arc.reader();
                if (weight != null) { // retrieve term frequency per matched document
                    Scorer scorer = weight.scorer(arc);
                    if (scorer == null) { // no matched documents
                        continue;
                    }
                    DocIdSetIterator documentIterator = scorer.iterator();
                    int doc;
                    while ((doc = documentIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                        Terms terms = reader.getTermVector(doc, field);
                        if (terms == null) {
                            throw new IllegalArgumentException("Field " + field + " has no Terms");
                        }

                        getTermFrequencies(terms.iterator(), searchTerms, freq);
                    }
                } else {
                    // all documents - use the fast path
                    getTermFrequencies(reader.terms(field).iterator(), searchTerms, freq);
                }
            }

            return freq;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Get the frequency of all terms in the TermsEnum or the frequency list and add them to the freq map.
     *
     * @param it the terms in the (set of) documents or a leaf
     * @param searchTerms list of terms whose frequencies to retrieve, or null/empty to retrieve for all terms
     * @param freq map containing existing frequencies to add on to or merge in to
     */
    private static void getTermFrequencies(TermsEnum it, Set<String> searchTerms, Map<String, Integer> freq) throws IOException {
        if (searchTerms != null && !searchTerms.isEmpty()) {
            for (String term : searchTerms) {
                if (it.seekExact(new BytesRef(term))) {
                    if (freq.containsKey(term)) {
                        freq.put(term, (int) (freq.get(term) + it.totalTermFreq()));
                    } else {
                        freq.put(term, (int) it.totalTermFreq());
                    }
                } else {
                    if (!freq.containsKey(term)) {
                        freq.put(term, 0);
                    }
                }
            }
        } else {
            BytesRef cur;
            while ((cur = it.next()) != null) {
                String term = cur.utf8ToString();
                if (freq.containsKey(term)) {
                    freq.put(term, (int) (freq.get(term) + it.totalTermFreq()));
                } else {
                    freq.put(term, (int) it.totalTermFreq());
                }
            }
        }
    }

    public static long getSumTotalTermFreq(IndexReader reader, String luceneField) {
        long totalTerms = 0;
        try {
            for (LeafReaderContext ctx : reader.leaves()) {
                Terms terms = ctx.reader().terms(luceneField);
                if (terms == null) {
                    // if this LeafReader doesn't include this field, just skip it
                    continue;
                }
                totalTerms += terms.getSumTotalTermFreq();
            }
            return totalTerms;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Return the maximum number of terms in any one LeafReader.
     *
     * This is a measure of the number of unique terms in the index for the specified field.
     *
     * It is not an exact number. We only use this to enable/disable certain optimizations for certain fields.
     *
     * @param reader index reader
     * @param fieldName Lucene field name
     * @return maximum number of terms in a LeafReader
     */
    public static long getMaxTermsPerLeafReader(IndexReader reader, String fieldName) {
        long maxTermsPerLeafReader = 0;
        try {
            for (LeafReaderContext leafReader : reader.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms != null && maxTermsPerLeafReader < terms.size())
                    maxTermsPerLeafReader = terms.size();
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
        return maxTermsPerLeafReader;
    }

    public static class SimpleDocIdCollector extends SimpleCollector {
        private final List<Integer> docIds;
        private int docBase;

        public SimpleDocIdCollector(List<Integer> docIds) {
            this.docIds = docIds;
        }

        @Override
        protected void doSetNextReader(LeafReaderContext context) throws IOException {
            docBase = context.docBase;
            super.doSetNextReader(context);
        }

        @Override
        public void collect(int docId) {
            int globalDocId = docId + docBase;
            docIds.add(globalDocId);
        }

        @Override
        public ScoreMode scoreMode() {
            return ScoreMode.COMPLETE_NO_SCORES;
        }
    }
}
