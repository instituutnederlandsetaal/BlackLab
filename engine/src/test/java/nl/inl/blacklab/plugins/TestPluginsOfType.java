package nl.inl.blacklab.plugins;

import java.io.File;
import java.util.List;
import java.util.Map;

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
        config.setPlugins(Map.of("TestPlugin", Map.of("message", "configured")));
        plugins = new PluginsOfType<>(TestPlugin.class, config, getClass().getClassLoader());
        plugin = new TestPlugin();
        plugins.register(plugin, config, "test-alias", true);
    }

    @Test
    public void testListingDoesNotInitialize() {
        PluginMetadata metadata = plugins.getAll().iterator().next();
        Assert.assertEquals(1, plugins.getAll().size());
        Assert.assertEquals("TestPlugin", metadata.id());
        Assert.assertEquals("test-plugin", metadata.name());
        Assert.assertEquals("Test description", metadata.description());
        Assert.assertEquals(List.of("first", "last"),
                metadata.parameters().stream().map(PluginParam::name).toList());
        Assert.assertEquals(0, plugin.configureCalls);
        Assert.assertEquals(0, plugin.initializeCalls);
    }

    @Test
    public void testFirstUseInitializesOnce() {
        plugins.getAll();
        Assert.assertSame(plugin, plugins.get("test-alias"));
        Assert.assertSame(plugin, plugins.getIfExists("test-plugin").orElseThrow());
        Assert.assertEquals(1, plugin.configureCalls);
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals("configured", plugin.cfgString("message"));
        Assert.assertTrue(plugins.getIfExists("missing").isEmpty());
        Assert.assertThrows(PluginException.class, () -> plugin.addParam(PString.any("late")));
    }

    @Test
    public void testInitializationFailureIsDeferredAndCached() {
        plugin.fail = true;
        Assert.assertEquals(1, plugins.getAll().size());
        PluginException first = Assert.assertThrows(PluginException.class, () -> plugins.get("test-alias"));
        Assert.assertSame(first, Assert.assertThrows(PluginException.class, () -> plugins.get("test-plugin")));
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertEquals(1, plugins.getAll().size());
    }

    @Test
    public void testEagerInitializationStillWorks() {
        plugins.initializePlugins();
        Assert.assertEquals(1, plugin.initializeCalls);
        Assert.assertSame(plugin, plugins.get("test-alias"));
        Assert.assertEquals(1, plugin.initializeCalls);
    }

    @Test
    public void testBuiltInParametersAvailableBeforeInitialization() {
        PluginMetadata metadata = new PluginMetadata(new ProcessingInstructionAppend());
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
        boolean fail;

        public TestPlugin() {
            setId("TestPlugin");
            addParam(PString.any("last"));
            addParam(PString.any("first", true));
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
            if (fail)
                throw new PluginException("Expected initialization failure");
        }
    }
}
