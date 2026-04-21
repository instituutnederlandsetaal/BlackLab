package nl.inl.util.fileprocessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.util.CurrentThreadExecutorService;
import nl.inl.util.ZipHandleManager;

/**
 * Process (trees of) files.
 * <p>
 * May include archives that we want to recursively process as well.
 * <p>
 * This class is thread-safe.
 */
public class FileProcessor implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(FileProcessor.class);

    public static FileReference fetchFileFromArchive(File f, final String pathInsideArchive) {
        if (f.getName().endsWith(".gz") || f.getName().endsWith(".tgz")) {
            // We have to process the whole file, we can't do random access.
            interface PathCapturingFileHandler extends FileHandler {
                FileReference getFile();
            }
            PathCapturingFileHandler fileHandler = new PathCapturingFileHandler() {
                FileReference fileRef;

                @Override
                public void file(FileReference file) {
                    if (file.getPath().endsWith(pathInsideArchive))
                        fileRef = file;
                }

                @Override
                public FileReference getFile() {
                    return fileRef;
                }
            };
            SimpleErrorHandler errorHandler = new SimpleErrorHandler(false);
            FileIterator.FileIteratorSettings settings = new FileIterator.FileIteratorSettings(false, true, "*");
            try (FileProcessor proc = new FileProcessor(fileHandler, errorHandler, 1, settings)) {
                proc.processFileOrDirectory(f);
            }
            // FileProcessor must have completed/be closed before result is available
            return fileHandler.getFile();
        } else if (f.getName().endsWith(".zip")) {
            // We can do random access. Fetch the file we want.
            try {
                ZipFile z = ZipHandleManager.acquire(f);
                try {
                    ZipEntry e = z.getEntry(pathInsideArchive);
                    if (e == null) {
                        throw new ErrorIndexingFile(
                                "Linked document " + pathInsideArchive + " not found in archive " + f);
                    }
                    try (InputStream is = z.getInputStream(e)) {
                        return FileReference.fromBytes(f.getCanonicalPath() + "/" + pathInsideArchive,
                                IOUtils.toByteArray(is), f);
                    }
                } finally {
                    ZipHandleManager.release(z);
                }
            } catch (IOException e) {
                throw new ErrorIndexingFile(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported archive type: " + f.getName());
        }
    }

    /** What to do with each file */
    private final FileHandler fileHandler;

    /** Decides whether to continue when an error occurs */
    private final ErrorHandler errorHandler;

    /** Number of simultaneous indexing threads */
    private final int numberOfThreadsToUse;

    /** Settings for finding files: recursive, process archives and glob pattern. */
    private final FileIterator.FileIteratorSettings settings;

    /**
     * Executor used for processing files, uses {@link CurrentThreadExecutorService} if
     * FileProcess was constructor with useThreads = false
     */
    private final ExecutorService executor;

    /** Our queue of files to process */
    private final List<FileIterator> toProcess = new ArrayList<>();

    /** Is adding more files to process no longer allowed? */
    private volatile boolean closed = false;

    /*
     * FileProcessor operates in two distinct stages:
     * <p>
     * - The traversal of directories/archives, this is done on the "main"
     * thread (i.e. the thread that initially added file(s)/directories to process)
     * - Handling of all files/entries, this is usually done asynchronously by our
     * Handler.
     * <p>
     * If an exception occurs in the handling stage, we want to stop all ongoing and
     * queued handlers. The problem is that the main can't
     * directly act on exceptions thrown in handlers, as the exception is thrown
     * asynchronously.
     * <p>
     * So we need a way to signal the main thread to cease all work:
     * <p>
     * - aborting all handlers/tasks is easy, we can shut down the ExcecutorService
     * directly from the handler thread when the exception occurs.
     * - aborting the main thread will require setting some flag and some manual
     * checking on its part we could call Thread.interrupt() on the main thread,
     * but this would require the handlers to keep a reference to the main thread
     * so instead just use this flag that the main thread checks while it's performing
     * work.
     */

    /**
     * Separate from closed to allow aborting even while already closed or closing.
     * This happens when an error occurs while processing remainder of queue. It's
     * also useful to allow aborting when closing unexpectedly takes a long time.
     */
    private volatile boolean aborted = false;

    public FileProcessor(FileHandler fileHandler, ErrorHandler errorHandler, final int numberOfThreadsToUse, FileIterator.FileIteratorSettings settings) {
        this.settings = settings;
        this.fileHandler = fileHandler;
        this.errorHandler = errorHandler == null ? new SimpleErrorHandler(false) : errorHandler;
        this.numberOfThreadsToUse = numberOfThreadsToUse;

        // We always use an ExecutorService to call our handlers to simplify our code
        // When not using threads, the service is just a fancy wrapper around doing task.run() directly inside the calling thread.
        if (numberOfThreadsToUse > 1) {
            // NOTE: we need to create our own executor instead of using Executors.newFixedThreadPool()
            // Because that implementation has an unbounded job queue by default, we run the risk that we queue 50k jobs and eat up all memory
            // So we provide our own queue that will block when it's full.
            // We could just provide a default LinkedBlockingDeque, but the default behavior for the executor is to reject all jobs while the queue is full.
            // To get around this, override the queue.offer() function used internally by the executor to queue jobs,
            // and make it blocking (instead of returning false instantly, which would make the executor reject the job)
            int cpuCores = Runtime.getRuntime().availableProcessors();
            int actualThreadsToUse = Math.max(1, Math.min(cpuCores - 1, numberOfThreadsToUse)); // no more than (cores-1), but at least 1
            executor = new ThreadPoolExecutor(actualThreadsToUse, actualThreadsToUse, Integer.MAX_VALUE, TimeUnit.DAYS,
                // We don't need a long queue at all
                // Every queued job holds a full document in memory, and documents can be *very* large (100Meg+)
                    new LinkedBlockingDeque<>(Math.max(1, actualThreadsToUse / 2)) {
                        @Override
                        public boolean offer(Runnable r) {
                            try {
                                return offer(r, Integer.MAX_VALUE, TimeUnit.DAYS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt(); // preserve interrupted status
                                return false;
                            }
                        }
                    }
            );

            // Never throw RejectedExecutionException in the main thread
            // (this can rarely happen when the FileProcessor shut down from another thread (usually a task thread that encountered an exception?)
            // just in between checking state and submitting)
            ((ThreadPoolExecutor) executor).setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        } else {
            executor = new CurrentThreadExecutorService((r, e) -> {
                /* swallow RejectedExecutionExceptions, same as above. */ });
        }
    }

    /**
     * Process files from a FileIterator.
     * <p>
     * Queues the files for processing, returns immediately.
     *
     * @param fileIterator iterator that provides files to process
     */
    public void process(FileIterator fileIterator) {
        synchronized (toProcess) {
            toProcess.add(fileIterator);
        }
        ensureThreadsRunning();
    }

    /** How many file handling threads are currently running. */
    AtomicInteger threadsRunning = new AtomicInteger(0);

    /** Make sure file handling threads are running. */
    private void ensureThreadsRunning() {
        if (!fileHandler.continueIndexing())
            aborted = true;
        while (threadsRunning.get() < numberOfThreadsToUse) {
            if (aborted)
                return;

            synchronized (toProcess) {
                if (toProcess.isEmpty())
                    return; // nothing to do
            }
            startThread();
        }
    }

    private void startThread() {
        Runnable runnable = () -> {
            // Make sure we never run more than numberOfThreadsToUse threads simultaneously
            while (true) {
                int current = threadsRunning.get();
                if (current >= numberOfThreadsToUse) {
                    return; // too many threads already
                }
                if (threadsRunning.compareAndSet(current, current + 1)) {
                    break; // successfully incremented
                }
            }
            // Handle files until there are no more
            try {
                while (!aborted && fileHandler.continueIndexing()) {
                    FileReference file = nextFile();
                    if (file == null)
                        break;
                    try {
                        fileHandler.file(file);
                    } catch (Exception e) {
                        reportAndAbort(e, file.getPath(), file.getAssociatedFile());
                    }
                }
            } finally {
                threadsRunning.decrementAndGet();
            }
        };
        CompletableFuture.runAsync(runnable, executor)
                .exceptionally(e -> reportAndAbort(e, null, null));
    }

    /**
     * Get the next file to process.
     * <p>
     * Called by worker threads when they're done processing their previous file.
     *
     * @return the next file, or null if there are no more files
     */
    private FileReference nextFile() {
        synchronized (toProcess) {
            while (!aborted && !toProcess.isEmpty()) {
                FileIterator it = toProcess.get(0);
                if (it.hasNext()) {
                    FileReference next = it.next();
                    if (next != FileReference.DUMMY) {
                        // FileIterator may return DUMMY to skip a file
                        // (this can make plugin implementation a little easier)
                        return next;
                    }
                } else {
                    toProcess.remove(0);
                    try {
                        it.close();
                    } catch (Exception e) {
                        reportAndAbort(e, null, null);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Process a file or directory.
     * <p>
     * If this file is a directory, all child files will be processed, files within
     * subdirectories will only be processed if settings.recurseSubdirs is true.
     * For rules on how files are processed, regarding archives etc, see
     * {@link #processFile(FileReference)}.
     *
     * @param file file, directory or archive to process
     */
    public void processFileOrDirectory(File file) {
        if (!file.exists()) {
            reportAndAbort(new FileNotFoundException("Input file or dir not found: " + file), file.getAbsolutePath(), file);
            return;
        }
        if (closed)
            throw new IllegalStateException("FileProcessor is closed, cannot process more files");
        process(FileIterator.from(file, settings));
    }

    /**
     * Process from an InputStream, which may be an archive or a regular file.
     * <p>
     * Archives (.zip and .tar.gz) will only be processed if
     * settings.processArchives is true. GZipped files (.gz) will be unpacked
     * regardless. Note that all files within archives will be processed, regardless
     * of whether they match settings.pattGlob.
     *
     * @param fileRef the file reference, with its path, a way to access its contents and optionally its associated file
     */
    public void processFile(FileReference fileRef) {
        if (closed)
            throw new IllegalStateException("FileProcessor is closed, cannot process more files");
        process(FileIterator.fileReferenceToFileIterator(fileRef, settings));
    }

    /**
     * Callback for when handler throws an exception. Report it, and if it's
     * irrecoverable, abort.
     * {@link ErrorHandler#errorOccurred(Throwable, String, File)}
     *
     * @return always null, has return type to enable use as exception handler in
     *         CompletableFuture
     */
    private synchronized Void reportAndAbort(Throwable e, String path, File f) {
        if (e instanceof CompletionException) // async exception
            e = e.getCause();

        // Only report the first fatal exception
        if (!aborted) {
            if (errorHandler == null) {
                logger.warn("WARNING: No errorHandler set for FileProcessor!");
                logger.warn(e);
            }
            if (errorHandler == null || !errorHandler.errorOccurred(e, path, f)) {
                abort();
            }
        }

        return null;
    }

    /**
     * Like {@link FileProcessor#close()} but immediately abort all running handler
     * tasks and cancel any pending tasks.
     * <p>
     * Subsequent calls to close, processFile or processInputStream will have no
     * effect.
     */
    // this function can't be synchronized on (this) or we couldn't abort from an async handler while the main thread is working/waiting on close().
    public void abort() {
        synchronized (this) {
            if (aborted)
                return;
            closed = true;
            aborted = true;
        }

        executor.shutdownNow();
    }

    /**
     * Close the executor and wait until all running and pending handler tasks have
     * completed. Calling close() while processFile or processInputStream is in
     * progress will cause them to skip all remaining files. Files for which a task
     * has already been put in the queue will still be processed as normal.
     * <p>
     * Subsequent calls to close, processFile or processInputStream will have no
     * effect.
     */
    @Override
    public void close() {
        synchronized (this) {
            boolean wasClosed = closed;
            closed = true;
            if (wasClosed)
                return; // was already closed
        }

        // Wait for the queue to empty
        while (!aborted) {
            synchronized (toProcess) {
                if (toProcess.isEmpty())
                    break;
            }
            ensureThreadsRunning();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw new RuntimeException(e);
            }
        }

        // Shutdown the executor and wait for all running tasks to complete
        try {
            executor.shutdown();
            // Outside the synchronized block to allow calling abort() while waiting for close() to complete
            // This is used by tasks that threw a fatal exception
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new IllegalStateException("Interrupted while waiting for processing threads to finish", e);
        }
    }
}
