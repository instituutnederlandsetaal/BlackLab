package nl.inl.blacklab.index;

import java.io.File;

import nl.inl.util.fileprocessor.ErrorHandler;

/**
 * Used to report progress while indexing, so we can give feedback to the user.
 */
public class IndexListener implements ErrorHandler {
    private long indexStartTime = -1;

    public long getIndexStartTime() {
        // NOTE: technically, this method should be synchronized, but it's called quite often and
        // in practice it doesn't matter if two threads set indexStartTime at the same time to the
        // same value.
        if (indexStartTime < 0)
            indexStartTime = System.currentTimeMillis();
        return indexStartTime;
    }

    private long closeStartTime;

    private long indexTime = 0;

    private long closeTime = 0;

    /** How many documents have been processed? */
    private int docsDone = 0;

    /** How many characters have been processed? */
    private long charsProcessed = 0;

    /** How many tokens (words) have been processed? */
    private long tokensProcessed = 0;

    /** How many files have been processed? */
    private long filesProcessed = 0;

    private long createTime;

    private long totalTime;

    private int errors = 0;

    /**
     * Started processing a file.
     *
     * @param name name of the file
     */
    public void fileStarted(String name) {
        //
    }

    public synchronized void fileDone(String name) {
        filesProcessed++;
    }

    /**
     * Some number of characters has been processed.
     *
     * (this should be called regularly by the indexing class, so we can provide
     * accurate progress reports)
     *
     * Synchronized to allow parallel indexing.
     *
     * @param charsDone the number of characters processed since the last call
     */
    public synchronized void charsDone(long charsDone) {
        charsProcessed += charsDone;
    }

    /**
     * We started processing a document
     *
     * Synchronized to allow parallel indexing.
     *
     * @param name name of the document
     */
    public void documentStarted(String name) {
        //
    }

    /**
     * A document was processed.
     *
     * Synchronized to allow parallel indexing.
     *
     * @param name name of the document
     */
    public synchronized void documentDone(String name) {
        docsDone++;
    }

    /**
     * Get the number of files processed so far.
     *
     * @return the number of files processed so far
     */
    public synchronized long getFilesProcessed() {
        return filesProcessed;
    }

    /**
     * Get the number of characters processed so far.
     *
     * @return the number of characters processed so far
     */
    public synchronized long getCharsProcessed() {
        return charsProcessed;
    }

    /**
     * Get the number of characters processed so far.
     *
     * @return the number of characters processed so far
     */
    public synchronized long getTokensProcessed() {
        return tokensProcessed;
    }

    /**
     * Get the number of documents processed so far.
     *
     * @return the number of documents processed so far
     */
    public synchronized long getDocsDone() {
        return docsDone;
    }

    /**
     * The indexing process started
     */
    public synchronized void indexStart() {
        // we don't set indexStartTime here, as gathering the list of files to index may take a few minutes,
        // which would throw off the indexing speed by a lot.
    }

    /**
     * The indexing process ended
     */
    public synchronized void indexEnd() {
        indexTime = System.currentTimeMillis() - getIndexStartTime();
    }

    /**
     * The close process started
     */
    public synchronized void closeStart() {
        closeStartTime = System.currentTimeMillis();
    }

    /**
     * The close process ended
     */
    public synchronized void closeEnd() {
        closeTime = System.currentTimeMillis() - closeStartTime;
    }

    public long getIndexTime() {
        return indexTime;
    }

    public long getCloseTime() {
        return closeTime;
    }

    public synchronized void indexerCreated(Indexer indexer) {
        createTime = System.currentTimeMillis();
    }

    public synchronized void indexerClosed() {
        totalTime = System.currentTimeMillis() - createTime;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public void documentAddedToIndex() {
        //
    }

    public synchronized void tokensDone(int n) {
        tokensProcessed += n;
    }

    @Override
    public synchronized boolean errorOccurred(Throwable e, String path, File f) {
        errors++;
        return true;
    }

    public int getErrors() {
        return errors;
    }

    /**
     * Changes will be rolled back (called after an error).
     */
    public void rollbackStart() {
        // (subclass may override this)
    }

    /**
     * Changes have been rolled back (called after an error).
     */
    public void rollbackEnd() {
        // (subclass may override this)
    }

    public void warning(String string) {
        // (subclass may override this)
    }

}
