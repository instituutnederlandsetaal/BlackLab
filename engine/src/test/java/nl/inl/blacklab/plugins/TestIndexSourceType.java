package nl.inl.blacklab.plugins;

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

    @Test
    public void testParseUri() {
        String[] parts;

        parts = IndexSourceType.parseUri("file:" + WIN_PATH);
        assert parts[0].equals("file");
        assert parts[1].equals(WIN_PATH);

        parts = IndexSourceType.parseUri(WIN_PATH);
        assert parts[0].isEmpty();
        assert parts[1].equals(WIN_PATH);

        parts = IndexSourceType.parseUri("file:" + PATH);
        assert parts[0].equals("file");
        assert parts[1].equals(PATH);

        parts = IndexSourceType.parseUri(PATH);
        assert parts[0].isEmpty();
        assert parts[1].equals(PATH);

        parts = IndexSourceType.parseUri("archive:test.zip/file.xml");
        assert parts[0].equals("archive");
        assert parts[1].equals("test.zip/file.xml");
    }
}
