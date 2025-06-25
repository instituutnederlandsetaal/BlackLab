package nl.inl.blacklab.tools.frequency.writers;

import de.siegmar.fastcsv.writer.CsvWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

import java.io.File;

import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.util.Timer;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

import static nl.inl.blacklab.tools.frequency.writers.FreqListWriter.getCsvWriter;

public class LookupTableWriter {
    public static void write(BlackLabIndex index, BuilderConfig bCfg, FreqListConfig fCfg) {

        System.out.println("Writing annotation id lookup tables");
        Timer t = new Timer();

        AnnotatedField annotatedField = index.annotatedField(bCfg.getAnnotatedField());
        bCfg.check(index);
        index.setCache(new SearchCacheDummy()); // don't cache results

        List<String> annotationNames = fCfg.getAnnotations();
        List<Annotation> annotations = annotationNames.stream().map(annotatedField::annotation)
                .toList();

        for (Annotation annotation: annotations) {
            String fileName = fCfg.getReportName() + "_" + annotation.name() + "_lookup.tsv" + (bCfg.isCompressed() ? ".lz4" : "");
            File outputFile = new File(bCfg.getOutputDir(), fileName);

            try (CsvWriter csv = getCsvWriter(outputFile, bCfg.isCompressed())) {
                Terms terms = index.annotationForwardIndex(annotation).terms();
                for (int i = 0; i < terms.numberOfTerms(); i++) {
                    MatchSensitivity matchSensitivity = MatchSensitivity.INSENSITIVE;
                    String sortString =  matchSensitivity.desensitize(terms.get(i));
                    csv.writeRecord(String.valueOf(i), sortString);
                }
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }

        System.out.println("Time: " + t.elapsedDescription());
    }
}
