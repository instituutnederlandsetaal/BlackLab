package nl.inl.blacklab.contentstore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.UtilsForTesting;

public class TestContentStoreDirFixedBlock {

    /** How many test operations to perform */
    private static final int OPERATIONS = 500;
    
    private ContentStoreExternal store;

    private UtilsForTesting.TestDir testDir;

    String[] str = { "The quick brown fox ", "jumps over the lazy ", "dog.      ", "Leentje leerde Lotje lopen lan" };

    final String[] doc = new String[4];

    private boolean currentlyWriteMode;

    @Before
    public void setUp() {

        // Create new test dir
        testDir = UtilsForTesting.createBlackLabTestDir("ContentStoreDirNew");

        try {
            store = new ContentStoreFixedBlockWriter(testDir.file(), true);
            try {

                // Create four different documents that span different numbers of 4K blocks.
                Random random = new Random(12_345);
                for (int i = 0; i < doc.length; i++) {
                    StringBuilder b = new StringBuilder();
                    for (int j = 0; j < i * 2400 + 800; j++) {
                        char c = (char) ('a' + random.nextInt(26));
                        b.append(c);
                    }
                    doc[i] = b.toString();
                }

                // Store strings
                for (int i = 0; i < doc.length; i++) {
                    Assert.assertEquals(i + 1, store.store(doc[i]));
                }
            } finally {
                store.close(); // close so everything is guaranteed to be written
            }
            store = new ContentStoreFixedBlockWriter(testDir.file(), false);
            currentlyWriteMode = true;
        } catch (ErrorOpeningIndex e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @After
    public void tearDown() {
        if (store != null)
            store.close();
        testDir.close();
    }

    @Test
    public void testRetrieve() {
        ensureMode(false);
        // Retrieve strings
        for (int i = 0; i < doc.length; i++) {
            Assert.assertEquals(doc[i], store.retrieve(i + 1));
        }
    }

    @Test
    public void testRetrievePart() {
        ensureMode(false);
        String[] parts = store.retrieveParts(2, new int[] { 5, 15 }, new int[] { 7, 18 });
        Assert.assertEquals(doc[1].substring(5, 7), parts[0]);
        Assert.assertEquals(doc[1].substring(15, 18), parts[1]);
    }

    @Test
    public void testDelete() {
        store.delete(2);
        ensureMode(false);
        Assert.assertThrows(IllegalArgumentException.class, () -> store.retrieve(2));
        Assert.assertEquals(doc[0], store.retrieve(1));
    }

    @Test
    public void testDeleteReuse() {
        store.delete(2);
        store.store(doc[3]);
        ensureMode(false);
        Assert.assertEquals(doc[3], store.retrieve(5));
    }

    @Test
    public void testDeleteReuseMultiple() throws IOException {
        // Keep track of which documents are stored under which ids
        List<Integer> storedKeys = new ArrayList<>();
        Map<Integer, String> stored = new HashMap<>();
        for (int i = 1; i <= doc.length; i++) {
            storedKeys.add(i);
            stored.put(i, doc[i - 1]);
        }

        // Perform some random delete/add operations
        Random random = new Random(23_456);
        boolean testedClear = false;
        for (int i = 0; i < 500; i++) {

            if (i >= OPERATIONS / 2 && !testedClear) {
                // Halfway through the test, clear the whole content store.
                testedClear = true;
                ensureMode(true);
                store.clear();
                stored.clear();
                storedKeys.clear();
            }

            if (!stored.isEmpty() && random.nextInt(3) == 0) {
                // Choose random document. Assert it was stored correctly, then delete it.
                int keyIndex = random.nextInt(stored.size());
                Integer key = storedKeys.remove(keyIndex);
                String docContents = stored.remove(key);
                ensureMode(false);
                assertDocumentStored(random, docContents, key);
                ensureMode(true);
                store.delete(key);
                Assert.assertTrue(store.isDeleted(key));
            } else {
                // Choose random document. Insert it and assert it was stored correctly.
                int docIndex = random.nextInt(doc.length);
                String docContents = doc[docIndex];
                ensureMode(true);
                int key = store.store(docContents);
                storedKeys.add(key);
                stored.put(key, docContents);
                ensureMode(false);
                assertDocumentStored(random, docContents, key);
            }
        }

        // Check that the status of ids in the store matches those in our storedKeys list
        Set<Integer> keysFromStore = store.idSet();
        int liveDocs = 0;
        for (Integer key : keysFromStore) {
            boolean isDeleted = store.isDeleted(key);
            if (!isDeleted)
                liveDocs++;
            Assert.assertEquals(isDeleted, !storedKeys.contains(key));
        }
        Assert.assertEquals(liveDocs, storedKeys.size());

        // Finally, check that all documents are still stored correctly
        ensureMode(false);
        for (int key : storedKeys) {
            String value = stored.get(key);
            assertDocumentStored(random, value, key);
        }
    }

    private void assertDocumentStored(Random random, String docContents, int key) {
        Assert.assertFalse(store.isDeleted(key));
        Assert.assertEquals(docContents.length(), store.docLength(key));
        if (random.nextBoolean()) {
            // Retrieve full document
            Assert.assertEquals(docContents, store.retrieve(key));
        } else {
            // Retrieve part of document
            int start = random.nextInt(docContents.length() - 10);
            int end = random.nextInt(docContents.length() - start) + start;
            String docPart = docContents.substring(start, end);
            String retrievedPart = store.retrievePart(key, start, end);
            Assert.assertEquals(docPart, retrievedPart);
        }
    }
    
    public void ensureMode(boolean write) {
        if (currentlyWriteMode == write)
            return;
        try {
            store.close();
            store = ContentStoreExternal.open(testDir.file(), write, false);
            currentlyWriteMode = write;
        } catch (ErrorOpeningIndex e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Test
    public void testCloseReopen() {
        ensureMode(false);
        Assert.assertEquals(doc[0], store.retrieve(1));
    }

    @Test
    public void testCloseReopenAppend() {
        Assert.assertEquals(5, store.store("test"));
    }
}
