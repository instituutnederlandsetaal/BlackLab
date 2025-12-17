package nl.inl.blacklab.plugins;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLab;

public class TestIndexSourceType {

    public static final String PATH = "/path/to/documents";

    public static final String WIN_PATH = "C:\\path\\to\\documents";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        BlackLab.implicitInstance(); // init plugin system
    }

    public void assertParse(String expectedScheme, String expectedPath, String uri) {
        String[] parts = IndexSourceType.parseUri(uri);
        Assert.assertEquals("Expected scheme '" + expectedScheme + "', got '" + parts[0] + "'", expectedScheme, parts[0]);
        Assert.assertEquals("Expected path '" + expectedPath + "', got '" + parts[1] + "'", expectedPath, parts[1]);
    }

    @Test
    public void testParseUri() {
        assertParse("file", WIN_PATH, "file:" + WIN_PATH);
        assertParse("file", WIN_PATH, "file://" + WIN_PATH);
        assertParse("", WIN_PATH, WIN_PATH);
        assertParse("file", PATH, "file:" + PATH);
        assertParse("", PATH, PATH);
        assertParse("archive", "test.zip/file.xml", "archive:test.zip/file.xml");
    }
}
