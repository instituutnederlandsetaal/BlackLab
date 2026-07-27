package nl.inl.blacklab.config;

import org.junit.Assert;
import org.junit.Test;

public class TestBLConfigIndexing {

    @Test
    public void testRamBufferSizeMB() {
        BLConfigIndexing config = new BLConfigIndexing();
        Assert.assertEquals(150.0, config.getRamBufferSizeMB(), 0.0);

        config.setRamBufferSizeMB(256.5);
        Assert.assertEquals(256.5, config.getRamBufferSizeMB(), 0.0);

        Assert.assertThrows(IllegalArgumentException.class, () -> config.setRamBufferSizeMB(0));
        Assert.assertThrows(IllegalArgumentException.class, () -> config.setRamBufferSizeMB(Double.NaN));
        Assert.assertThrows(IllegalArgumentException.class,
                () -> config.setRamBufferSizeMB(Double.POSITIVE_INFINITY));
    }
}
