package nl.inl.blacklab.performance;

import java.io.File;
import java.io.IOException;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.HitsSimple;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.FileUtil;
import nl.inl.util.Timer;

/**
 * Executes a batch of CorpusQL queries and times them. Doesn't do anything with
 * the hits found. Can optionally (-t) also skip reporting the total number of
 * hits (this saves time for large result sets)
 */
public class BatchQuery {

    private BatchQuery() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex, IOException {

        boolean determineTotalHits = true;
        int fileArgNumber = 0;
        File indexDir = null, inputFile = null;
        for (String s : args) {
            String arg = s.trim();
            if (arg.charAt(0) == '-') {
                if (arg.equals("-t")) {
                    determineTotalHits = false;
                } else {
                    System.err.println("Illegal option: " + arg);
                    usage();
                    return;
                }
            } else {
                switch (fileArgNumber) {
                    case 0:
                        indexDir = new File(arg);
                        if (!indexDir.exists() || !indexDir.isDirectory()) {
                            System.err.println("Index directory not found: " + arg);
                            usage();
                            return;
                        }
                        break;
                    case 1:
                        inputFile = new File(arg);
                        if (!inputFile.exists()) {
                            System.err.println("Input file not found: " + arg);
                            usage();
                            return;
                        }
                        break;
                    default:
                        System.err.println("Too many file arguments (supply index dir and input file)");
                        usage();
                        return;
                }
                fileArgNumber++;
            }
        }
        if (fileArgNumber < 2) {
            System.err.println("Too few file arguments (supply index dir and input file)");
            usage();
            return;
        }

        System.err.print("Opening index... ");
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            System.err.println("done.");
    
            System.out.print("Query\tSearch Time");
            if (determineTotalHits) {
                System.out.print("\t# Hits\tTotal Time");
            }
            System.out.println();
    
            for (String query : FileUtil.readLines(inputFile)) {
                query = query.trim();
                if (query.isEmpty() || query.charAt(0) == '#')
                    continue; // skip empty lines and #-comments
                try {
                    Timer t = new Timer();
                    System.out.print(query + "\t");
                    HitsSimple hits = index.find(CorpusQueryLanguageParser.parse(query, index.mainAnnotatedField().mainAnnotation().name()).toQuery(QueryInfo.create(index)), null);
                    System.out.print(t.elapsed());
                    if (determineTotalHits) {
                        System.out.print("\t" + hits.size() + "\t" + t.elapsed());
                    }
                    System.out.println();
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    System.err.println("Error with query " + query + "; skipping...");
                }
            }
        }
    }

    private static void usage() {
        System.err.println("\nUsage: " + BatchQuery.class.getSimpleName()
                + " [options] <indexdir> <inputfile>\n\n"
                + "<inputfile> should contain CQL queries, one per line.\n"
                + "\n"
                + "Options:\n"
                + "-t do not determine total number of hits\n"
                + "\n"
                + "Output:\n"
                + "<query>\t<searchTimeMs>\n"
                + "<query>\t<searchTimeMs>\t<totalHits>\t<totalTimeMs>\n");
    }
}
