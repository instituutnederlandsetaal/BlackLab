package nl.inl.blacklab.search.datastream;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.datastream.DataStreamAbstract;
import nl.inl.blacklab.server.lib.results.ApiVersion;

public class TestDataStream {

    DataStream jsonStream() {
        return DataStreamAbstract.create(DataFormat.JSON,  false, ApiVersion.CURRENT);
    }

    @Test
    public void testSimpleValues() {
        DataStream dataStream = jsonStream();
        dataStream.value(1);
        Assert.assertEquals("1", dataStream.getOutput());
        dataStream = jsonStream();
        dataStream.value("test\"");
        Assert.assertEquals("\"test\\\"\"", dataStream.getOutput());
    }

    @Test
    public void testList() {
        DataStream dataStream = jsonStream();
        dataStream.value(List.of(1, 2, 3));
        Assert.assertEquals("[1,2,3]", dataStream.getOutput());

        dataStream = jsonStream();
        dataStream.value(List.of("aap", "noot"));
        Assert.assertEquals("[\"aap\",\"noot\"]", dataStream.getOutput());
    }

    @Test
    public void testMap() {
        Map<String, Integer> m = new TreeMap<>(Map.of("test", 1, "noot", 2, "mies", 3));
        DataStream dataStream = jsonStream();
        dataStream.value(m);
        Assert.assertEquals("{\"mies\":3,\"noot\":2,\"test\":1}", dataStream.getOutput());
    }

}
