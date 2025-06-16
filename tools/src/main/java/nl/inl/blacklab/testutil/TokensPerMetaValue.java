package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.Map;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.analysis.BuiltinAnalyzers;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.resultproperty.DocPropertyAnnotatedFieldLength;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.util.LuceneUtil;

/**
 * Determine the number of tokens in the subcorpus defined by each of the
 * metadatafield values. (Only for those metadata fields that have a limited
 * number of values, all of which were captured in the index metadata file).
 */
public class TokensPerMetaValue {

    public static void main(String[] args) throws ParseException, ErrorOpeningIndex {

        String indexDir = "/home/jan/blacklab/gysseling/index";
        if (args.length >= 1)
            indexDir = args[0];
        String annotatedFieldName = "contents";
        if (args.length >= 2)
            annotatedFieldName = args[1];

        try (BlackLabIndex index = BlackLab.open(new File(indexDir))) {
            // Loop over all metadata fields
            IndexMetadata indexMetadata = index.metadata();
            System.out.println("field\tvalue\tnumberOfDocs\tnumberOfTokens");
            for (MetadataField field: indexMetadata.metadataFields()) {
                // Check if this field has only a few values
                MetadataFieldValues values = field.values(50);
                if (!values.valueList().isTruncated()) {
                    // Loop over the values
                    for (Map.Entry<String, Long> entry: values.valueList().getValues().entrySet()) {
                        // Determine token count for this value
                        Query filter = LuceneUtil.parseLuceneQuery(index, "\"" + entry.getKey().toLowerCase() + "\"",
                                BuiltinAnalyzers.DUTCH.getAnalyzer(), field.name());
                        DocResults docs = index.queryDocuments(filter);
                        long totalNumberOfTokens = docs.intSum(new DocPropertyAnnotatedFieldLength(index, annotatedFieldName));
                        for (DocResult doc: docs) {

                        }
                        System.out.println(field.name() + "\t" + entry.getKey() + "\t" + entry.getValue() + "\t"
                                + totalNumberOfTokens);
                    }
                }
            }
        }
    }
}
