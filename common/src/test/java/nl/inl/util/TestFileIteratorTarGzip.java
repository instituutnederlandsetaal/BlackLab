package nl.inl.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Random;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.input.CountingInputStream;
import org.junit.Test;

import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileIteratorTarGzip;
import nl.inl.util.fileprocessor.FileReference;

public class TestFileIteratorTarGzip {

    private static FileReference archive() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
            addEntry(tar, "first.txt", "first".getBytes(StandardCharsets.UTF_8));
            tar.putArchiveEntry(new TarArchiveEntry("dir/"));
            tar.closeArchiveEntry();
            addEntry(tar, "dir/empty.txt", new byte[0]);
            addEntry(tar, "dir/last.txt", "last".getBytes(StandardCharsets.UTF_8));
            tar.putArchiveEntry(new TarArchiveEntry("trailing-dir/"));
            tar.closeArchiveEntry();
        }
        return FileReference.fromBytes("archive.tar.gz", bytes.toByteArray(), null);
    }

    private static void addEntry(TarArchiveOutputStream tar, String name, byte[] contents) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(contents.length);
        tar.putArchiveEntry(entry);
        tar.write(contents);
        tar.closeArchiveEntry();
    }

    @Test
    public void readsEntriesIndependentlyAndStopsAtEnd() throws IOException {
        FileIteratorTarGzip iterator = new FileIteratorTarGzip(archive(), null, FileIterator.FileIteratorSettings.DUMMY);
        try {
            assertTrue(iterator.hasNext());
            FileReference first = iterator.next();
            assertEquals("archive.tar.gz/first.txt", first.getPath());
            assertTrue(iterator.hasNext());
            FileReference empty = iterator.next();
            assertEquals("archive.tar.gz/dir/empty.txt", empty.getPath());
            assertTrue(iterator.hasNext());
            FileReference last = iterator.next();
            assertEquals("archive.tar.gz/dir/last.txt", last.getPath());
            assertFalse(iterator.hasNext());
            assertFalse(iterator.hasNext());
            try {
                iterator.next();
                fail("Expected NoSuchElementException");
            } catch (NoSuchElementException expected) {
                // Iterator is exhausted.
            }
            assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), first.getSinglePassInputStream().readAllBytes());
            assertArrayEquals(new byte[0], empty.getSinglePassInputStream().readAllBytes());
            assertArrayEquals("last".getBytes(StandardCharsets.UTF_8), last.getSinglePassInputStream().readAllBytes());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void readsOnlyRequestedEntry() throws IOException {
        FileIteratorTarGzip iterator = new FileIteratorTarGzip(archive(), "dir/empty.txt",
                FileIterator.FileIteratorSettings.DUMMY);
        try {
            assertTrue(iterator.hasNext());
            FileReference entry = iterator.next();
            assertEquals("archive.tar.gz/dir/empty.txt", entry.getPath());
            assertArrayEquals(new byte[0], entry.getSinglePassInputStream().readAllBytes());
            assertFalse(iterator.hasNext());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void missingEntryIsExhausted() throws IOException {
        FileIteratorTarGzip iterator = new FileIteratorTarGzip(archive(), "missing.txt",
                FileIterator.FileIteratorSettings.DUMMY);
        try {
            assertFalse(iterator.hasNext());
        } finally {
            iterator.close();
        }
    }

    @Test
    public void doesNotReadEntireArchiveForFirstEntry() throws IOException {
        byte[] laterContents = new byte[1024 * 1024];
        new Random(42).nextBytes(laterContents);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(bytes))) {
            addEntry(tar, "first.txt", "first".getBytes(StandardCharsets.UTF_8));
            addEntry(tar, "later.bin", laterContents);
        }
        byte[] compressed = bytes.toByteArray();
        CountingInputStream source = new CountingInputStream(new ByteArrayInputStream(compressed));
        FileReference archive = FileReference.fromInputStream("archive.tar.gz", source, null);
        FileIteratorTarGzip iterator = new FileIteratorTarGzip(archive, null, FileIterator.FileIteratorSettings.DUMMY);
        try {
            FileReference first = iterator.next();
            assertArrayEquals("first".getBytes(StandardCharsets.UTF_8), first.getSinglePassInputStream().readAllBytes());
            assertTrue("Reading the first entry should not consume the whole archive",
                    source.getByteCount() < compressed.length / 2);
            assertArrayEquals(laterContents, iterator.next().getSinglePassInputStream().readAllBytes());
            assertFalse(iterator.hasNext());
        } finally {
            iterator.close();
        }
    }
}
