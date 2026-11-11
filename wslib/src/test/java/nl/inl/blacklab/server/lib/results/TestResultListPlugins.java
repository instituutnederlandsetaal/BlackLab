package nl.inl.blacklab.server.lib.results;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.BlackLab;

public class TestResultListPlugins {

    @Test
    public void testListingInitializesMetadataWithoutPreparingPlugins() {
        boolean delayInitialization = BlackLab.config().getPlugins().isDelayInitialization();
        BlackLab.config().getPlugins().setDelayInitialization(true);
        PluginManager.ensureInitialized();
        BlackLab.config().getPlugins().setDelayInitialization(delayInitialization);
        FileConverter converter = new FileConverter() {
            @Override
            public void initialize() {
                addParam(PString.any("last"));
                addParam(PString.any("first", true));
            }

            @Override
            public String getId() {
                return "test-list-without-initialization";
            }

            @Override
            public String getDescription() {
                return "Unprepared converter";
            }

            @Override
            public void prepare() {
                Assert.fail("Listing plugins must not prepare them");
            }
        };
        PluginManager.type(FileConverter.class).add(converter.getId(), converter);

        ResultListPlugins result = new ResultListPlugins();
        ResultListPlugins.PluginInfo info = result.getPluginsByType().get("FileConverter").stream()
                .filter(p -> p.getName().equals(converter.getName())).findFirst().orElseThrow();
        Assert.assertEquals(converter.getDescription(), info.getDescription());
        Assert.assertEquals(List.of("first", "last"), List.copyOf(info.getParamsMap().keySet()));
        Assert.assertEquals("string", info.getParamsMap().get("first").getType());
        Assert.assertTrue(info.getParamsMap().get("first").isRequired());

        ResultListPlugins.PluginInfo append = result.getPluginsByType().get("ProcessingInstruction").stream()
                .filter(p -> p.getName().equals("append")).findFirst().orElseThrow();
        Assert.assertEquals(List.of("field", "prefix", "separator", "value"),
                List.copyOf(append.getParamsMap().keySet()));
    }
}
