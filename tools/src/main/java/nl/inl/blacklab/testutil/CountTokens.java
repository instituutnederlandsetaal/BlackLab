package nl.inl.blacklab.testutil;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.Level;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;
import nl.inl.util.LogUtil;

/**
 * Calculate total token count (for older BlackLab indices that don't store this
 * in the metadata file).
 */
public class CountTokens {

    private final class CountTask implements DocTask {

        AtomicInteger totalDocs = new AtomicInteger(0);

        public AtomicLong totalTokens = new AtomicLong(0);

        CountTask() {
        }

        public void document(LeafReaderContext segment, int segmentDocId) {
            totalDocs.incrementAndGet();
            int globalDocId = segment.docBase + segmentDocId;
            totalTokens.getAndUpdate(n -> n + Long.parseLong(index.luceneDoc(globalDocId).get(tokenLengthField)));
            if (totalDocs.get() % 100 == 0) {
                System.out.println(totalDocs + " docs exported...");
            }
        }
    }

    public static void main(String[] args) throws ErrorOpeningIndex {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 1) {
            System.out.println("Usage: CountTokens <indexDir>");
            System.exit(1);
        }

        File indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            System.out.println("Directory doesn't exist or is unreadable: " + indexDir);
            System.exit(1);
        }
        if (!BlackLabIndex.isIndex(indexDir)) {
            System.out.println("Not a BlackLab index: " + indexDir);
            System.exit(1);
        }

        CountTokens exportCorpus = new CountTokens(indexDir);
        System.out.println("Calling export()...");
        exportCorpus.count();
    }

    BlackLabIndex index;

    private String tokenLengthField;

    public CountTokens(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        tokenLengthField = index.mainAnnotatedField().tokenLengthField();
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     */
    private void count() {
        System.out.println("Getting IndexReader...");
        System.out.println("Calling forEachDocument()...");
        CountTask task = new CountTask();
        index.forEachDocument(true, task);
        System.out.println("TOTAL TOKENS: " + task.totalTokens);
    }
}
