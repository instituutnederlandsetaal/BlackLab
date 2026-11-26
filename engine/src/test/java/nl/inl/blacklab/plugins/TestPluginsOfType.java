package nl.inl.blacklab.plugins;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.config.BLConfigPlugins;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionAppend;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.plugins.param.PluginParam;
import nl.inl.blacklab.search.TextPatternContext;
import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestPluginsOfType {

    private PluginsOfType<TestPlugin> plugins;

    private TestPlugin plugin;

    @Before
    public void setUp() {
        PluginManager.ensureInitialized();
        BLConfigPlugins config = new BLConfigPlugins();
        config.setDelayInitialization(true);
        config.setPlugins(Map.of("TestPlugin", Map.of("message", "configured", "maxLength", 42)));
        plugins = new PluginsOfType<>(TestPlugin.class, config, getClass().getClassLoader());
        plugin = new TestPlugin();
        plugins.register(plugin, config, "test-alias", true);
    }

    @Test
    public void testListingInitializesMetadataWithoutPreparing() {
        PluginMetadata metadata = plugins.getAllMetadata().iterator().next();
        Assert.assertEquals(1, plugins.getAllMetadata().size());
        Assert.assertEquals("TestPlugin", metadata.id());
        Assert.assertEquals("test-plugin", metadata.name());
        Assert.assertEquals("Test description", metadata.description());
        Assert.assertEquals(List.of("first", "last"),
                metadata.parameters().stream().map(PluginParam::name).toList());
        Assert.assertEquals(42, ((PString) metadata.parameters().get(0)).maxLength());
        Assert.assertEquals(1, plugin.configureCalls);
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(0, plugin.prepareCalls);
        Assert.assertThrows(PluginException.class, () -> plugin.addParam(PString.any("late")));
    }

    @Test
    public void testFirstUsePreparesOnce() {
        plugins.getAllMetadata();
        Assert.assertSame(plugin, plugins.get("test-alias"));
        Assert.assertSame(plugin, plugins.getIfExists("test-plugin").orElseThrow());
        Assert.assertEquals(1, plugin.configureCalls);
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
        Assert.assertEquals("configured", plugin.cfgString("message"));
        Assert.assertTrue(plugins.getIfExists("missing").isEmpty());
        Assert.assertThrows(PluginException.class, () -> plugin.addParam(PString.any("late")));
    }

    @Test
    public void testPreparationFailureIsDeferredAndCached() {
        plugin.preparationFailure = new PluginException("Expected preparation failure");
        Assert.assertEquals(1, plugins.getAllMetadata().size());
        PluginException first = Assert.assertThrows(PluginException.class, () -> plugins.get("test-alias"));
        Assert.assertSame(first, Assert.assertThrows(PluginException.class, () -> plugins.get("test-plugin")));
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
        Assert.assertEquals(1, plugins.getAllMetadata().size());
    }

    @Test
    public void testUnexpectedPreparationFailureIsCached() {
        plugin.preparationFailure = new IllegalStateException("Unexpected preparation failure");
        PluginException first = Assert.assertThrows(PluginException.class, () -> plugins.get("test-alias"));
        Assert.assertSame(plugin.preparationFailure, first.getCause());
        Assert.assertSame(first, Assert.assertThrows(PluginException.class, () -> plugins.get("test-plugin")));
        Assert.assertEquals(1, plugin.prepareCalls);
        Assert.assertEquals(1, plugins.getAllMetadata().size());
    }

    @Test
    public void testConcurrentUsePreparesOnce() {
        List<CompletableFuture<TestPlugin>> requests = IntStream.range(0, 16)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> plugins.get("test-alias"))).toList();
        requests.forEach(request -> Assert.assertSame(plugin, request.join()));
        Assert.assertEquals(1, plugin.configureCalls);
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
    }

    @Test
    public void testInitializationFailureIsCached() {
        plugin.failInitialization = true;
        PluginException first = Assert.assertThrows(PluginException.class, () -> plugins.get("test-alias"));
        Assert.assertSame(first, Assert.assertThrows(PluginException.class, () -> plugins.get("test-plugin")));
        Assert.assertTrue(plugins.getAllMetadata().isEmpty());
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(0, plugin.prepareCalls);
    }

    @Test
    public void testFirstUseWithoutListing() {
        Assert.assertSame(plugin, plugins.get("test-alias"));
        Assert.assertEquals(1, plugin.configureCalls);
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
    }

    @Test
    public void testEagerPreparationStillWorks() {
        plugins.preparePlugins();
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
        Assert.assertSame(plugin, plugins.get("test-alias"));
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugin.prepareCalls);
    }

    @Test
    public void testBuiltInParametersAvailableAfterInitialization() {
        ProcessingInstructionAppend append = new ProcessingInstructionAppend();
        append.initialize();
        PluginMetadata metadata = new PluginMetadata(append);
        Assert.assertEquals(List.of("field", "prefix", "separator", "value"),
                metadata.parameters().stream().map(PluginParam::name).toList());
    }

    @Test
    public void testQueryFunctionParameterOrder() {
        QueryFunction function = new QueryFunction("test", "Test function",
                List.of(PString.any("last"), PString.any("first"))) {
            @Override
            protected TextPattern.EvalResult applyFunc(TextPatternContext context, List<Object> parameters) {
                throw new UnsupportedOperationException();
            }
        };
        PluginMetadata metadata = new PluginMetadata(function);
        Assert.assertEquals(List.of("last", "first"),
                metadata.parameters().stream().map(PluginParam::name).toList());
        Assert.assertThrows(UnsupportedOperationException.class, () -> metadata.parameters().clear());
    }

    public static class TestPlugin extends Plugin {
        int configureCalls;
        int initializeCalls;
        int prepareCalls;
        boolean failInitialization;
        RuntimeException preparationFailure;

        public TestPlugin() {
            setId("TestPlugin");
        }

        @Override
        public String getName() {
            return "test-plugin";
        }

        @Override
        public String getDescription() {
            return "Test description";
        }

        @Override
        public void configure(Map<String, Object> config, File pluginDir) {
            configureCalls++;
            super.configure(config, pluginDir);
        }

        @Override
        public void initialize() {
            initializeCalls++;
            if (failInitialization)
                throw new PluginException("Expected initialization failure");
            addParam(PString.any("last"));
            addParam(PString.any("first", true, cfgInt("maxLength")));
            Assert.assertEquals("configured", cfgString("message"));
        }

        @Override
        public void prepare() {
            prepareCalls++;
            Assert.assertEquals(1, initializeCalls);
            Assert.assertThrows(PluginException.class, () -> addParam(PString.any("late")));
            if (preparationFailure != null)
                throw preparationFailure;
        }
    }
}
