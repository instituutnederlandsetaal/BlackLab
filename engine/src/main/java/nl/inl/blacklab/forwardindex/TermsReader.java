package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;

import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.util.BlockTimer;

/** Keeps a list of unique terms and their sort positions.
 *
 * This version is kept in external files.
 */
public class TermsReader extends TermsReaderAbstract {

    private final File termsFile;

    public TermsReader(Collators collators, File termsFile) {
        super(collators);
        this.termsFile = termsFile;

        try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
            try (FileChannel fc = raf.getChannel()) {
                read(fc);
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private void read(FileChannel fc) throws IOException {
        boolean traceIndexOpening = BlackLab.config().getLog().getTrace().isIndexOpening();
        try (BlockTimer ignored = BlockTimer.create(traceIndexOpening, "Initializing terms " + this.termsFile)) {
            long fileLength = termsFile.length();

            // Read numberOfTerms and terms[] String array
            Pair<IntBuffer, String[]> termsResult = TermsExternalUtil.readTermsFromFileChannel(fc, fileLength); // will allocate and fill this.terms
            IntBuffer ib = termsResult.getLeft();
            String[] terms = termsResult.getRight();
            int numberOfTerms = terms.length;

            // Read the sort order arrays (term id -> sort position, sensitive and insensitive)
            int[] termId2SensitivePosition = new int[numberOfTerms];
            int[] termId2InsensitivePosition = new int[numberOfTerms];
            ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
            ib.get(termId2SensitivePosition);
            ib.position(ib.position() + numberOfTerms); // Advance past unused sortPos -> id array (left in there for file compatibility)
            ib.get(termId2InsensitivePosition);

            finishInitialization(termsFile.getAbsolutePath().toString(), terms, termId2SensitivePosition,
                    termId2InsensitivePosition);

            //logger.debug("finishing initializing termsreader " + termsFile + " - " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) + "ms to process " + numberOfTerms + " terms");
        }
    }

    @Override
    public int[] segmentIdsToGlobalIds(int ord, int[] snippet) {
        return snippet; // no need to do anything, term ids are always global
    }

    @Override
    public int segmentIdToGlobalId(int ord, int segmentTermId) {
        return segmentTermId;
    }
}
