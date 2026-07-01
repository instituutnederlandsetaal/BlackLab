package nl.inl.blacklab.server.index;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.search.SearchManager;

public class TestIndexManagerCleanup {

    @Test
    public void testCleanupStopsRemovedIndicesMonitor() throws Exception {
        Path indexDir = Files.createTempDirectory("blacklab-indexmanager-cleanup");
        IndexManager indexManager = null;
        try {
            BLSConfig config = new BLSConfig();
            config.setIndexLocations(List.of(indexDir.toString()));

            indexManager = new IndexManager(Mockito.mock(SearchManager.class), config);
            FileAlterationMonitor monitor = (FileAlterationMonitor)getPrivateField(indexManager, "removedIndicesMonitor");

            Assert.assertNotNull(monitor);
            Assert.assertTrue(isMonitorRunning(monitor));

            indexManager.cleanup();

            Assert.assertFalse(isMonitorRunning(monitor));
            Assert.assertNull(getPrivateField(indexManager, "removedIndicesMonitor"));
        } finally {
            if (indexManager != null)
                indexManager.cleanup();
            FileUtils.deleteDirectory(indexDir.toFile());
        }
    }

    private static boolean isMonitorRunning(FileAlterationMonitor monitor) throws Exception {
        Thread thread = (Thread)getPrivateField(monitor, "thread");
        return (boolean)getPrivateField(monitor, "running") || thread != null && thread.isAlive();
    }

    private static Object getPrivateField(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
