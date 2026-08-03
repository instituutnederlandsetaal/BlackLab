package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestFileUtil {
    @Test
    public void testGlobToRegex() {
        Assert.assertEquals("^.*\\.txt$", FileUtil.globToRegex("*.txt"));
        Assert.assertEquals("^bla..\\.csv$", FileUtil.globToRegex("bla??.csv"));
        Assert.assertEquals("^b\\$\\^\\.txt$", FileUtil.globToRegex("b$^.txt"));
    }
}
