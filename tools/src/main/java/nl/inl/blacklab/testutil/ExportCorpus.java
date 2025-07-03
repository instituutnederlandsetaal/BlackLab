package nl.inl.blacklab.testutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.apache.logging.log4j.Level;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocTask;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;

/** Export the original corpus from a BlackLab index. */
public class ExportCorpus implements AutoCloseable {

    public static void main(String[] args) {
        LogUtil.setupBasicLoggingConfig(Level.DEBUG);

        if (args.length != 2) {
            System.out.println("Usage: ExportCorpus <indexDir> <exportDir>");
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

        File exportDir = new File(args[1]);
        if (!exportDir.isDirectory() || !exportDir.canWrite()) {
            System.out.println("Directory doesn't exist or cannot write to it: " + exportDir);
            System.exit(1);
        }

        try (ExportCorpus exportCorpus = new ExportCorpus(indexDir)) {
            System.out.println("Calling export()...");
            exportCorpus.export(exportDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    BlackLabIndex index;

    public ExportCorpus(File indexDir) throws ErrorOpeningIndex {
        System.out.println("Open index " + indexDir + "...");
        index = BlackLab.open(indexDir);
        System.out.println("Done.");
    }

    /**
     * Export the whole corpus.
     * 
     * @param exportDir directory to export to
     */
    private void export(final File exportDir) {

        System.out.println("Getting IndexReader...");
        final IndexReader reader = index.reader();

        System.out.println("Calling forEachDocument()...");
        index.forEachDocument(new DocTask() {

            final int totalDocs = reader.maxDoc() - reader.numDeletedDocs();

            int docsDone = 0;

            @Override
            public void perform(BlackLabIndex index, int id) {
                Document doc = index.luceneDoc(id);
                String fromInputFile = doc.get("fromInputFile");
                System.out.println("Getting content for " + fromInputFile + "...");
                try {
                    String xml = DocUtil.contents(index, index.mainAnnotatedField(), id, doc);
                    File file = new File(exportDir, fromInputFile);
                    System.out.println("Got content, exporting to " + file + "...");
                    if (file.exists()) {
                        // Add a number so we don't have to overwrite the previous file.
                        System.out.println(
                                "WARNING: File " + file + " exists, using different name to avoid overwriting...");
                        file = FileUtil.addNumberToExistingFileName(file);
                    }
                    System.out.println(file);
                    File dir = file.getAbsoluteFile().getParentFile();
                    if (!dir.exists()) {
                        if (!dir.mkdirs()) // create any subdirectories required
                            throw new RuntimeException("Could not create dir(s): " + dir);
                    }
                    try (PrintWriter pw = FileUtil.openForWriting(file)) {
                        pw.write(xml);
                    } catch (FileNotFoundException e) {
                        throw BlackLabException.wrapRuntime(e);
                    }
                } catch (RuntimeException e) {
                    // HACK: a bug in an older content store implementation can cause this
                    //   when exporting an older index. Report and continue.
                    System.out.flush();
                    e.printStackTrace(System.err);
                    System.err.println("### Error exporting " + fromInputFile + ", skipping ###");
                    System.err.flush();
                }
                docsDone++;
                if (docsDone % 100 == 0) {
                    int perc = docsDone * 100 / totalDocs;
                    System.out.println(docsDone + " docs exported (" + perc + "%)...");
                }
            }
        });
    }

    @Override
    public void close() {
        if (index != null)
            index.close();
    }
}
