package nl.inl.blacklab.index;

import java.util.Arrays;
import java.util.stream.Collectors;

import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileReference;

/** A silly test IndexSource.
 *
 * Given a URI like "test:word1 word2 word3", it produces a simple TEI XML file
 * with these words that can be indexed.
 */
@SuppressWarnings("unused") // Used by reflection to find this class
public class IndexSourceTypeTest extends IndexSourceType {

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "Generate a simple TEI XML file from a list of words.";
    }

    @Override
    public IndexSource get(String path, PluginParams params) {
        return new IndexSourceTest(path);
    }

    protected static class IndexSourceTest extends IndexSource {

        private final String content;

        public IndexSourceTest(String path) {
            super(path);
            content = "<TEI><text>" + Arrays.stream(path.split("\\s+", -1))
                    .map(word -> "<w>" + word + "</w>")
                    .collect(Collectors.joining("\n")) + "</text></TEI>";
        }

        @Override
        public FileIterator filesToIndex() {
            FileReference file = FileReference.fromCharArray("/test.xml", content.toCharArray(), null);
            return FileIterator.from(file, getFileIteratorSettings());
        }
    }
}
