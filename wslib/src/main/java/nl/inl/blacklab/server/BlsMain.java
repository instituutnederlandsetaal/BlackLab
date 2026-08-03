package nl.inl.blacklab.server;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.plugins.AuthMethodProvider;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.queryParser.JsonParserProvider;
import nl.inl.blacklab.queryParser.contextql.ContextQLParserProvider;
import nl.inl.blacklab.queryParser.corpusql.BcqlParserProvider;
import nl.inl.blacklab.server.auth.AuthClarinEppn;
import nl.inl.blacklab.server.auth.AuthDebugFixed;
import nl.inl.blacklab.server.auth.AuthDebugUrl;
import nl.inl.blacklab.server.auth.AuthHttpBasic;
import nl.inl.blacklab.server.auth.AuthRequestValue;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.config.BLSConfigDebug;
import nl.inl.blacklab.server.config.ConfigFileReader;
import nl.inl.blacklab.server.datastream.DataFormat;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Servlet-independent "main" BLS class. We can use this for other implementations as well.
 */
public class BlsMain {

    private static final Logger logger = LogManager.getLogger(BlsMain.class);

    private static final String CONFIG_FILE_NAME = "blacklab-server";

    private static BlsMain instance;

    /** Get instance, creating one if it doesn't exist yet */
    public static synchronized BlsMain get() {
        if (instance == null)
            instance = new BlsMain();
        return instance;
    }

    /** Get the instance if it exists */
    public static BlsMain getInstance() {
        return instance;
    }

    /**
     * Manages all our searches
     */
    private SearchManager searchManager;

    /**
     * Default output type to use if none given.
     */
    private DataFormat defaultOutputType;

    private BlsMain() {

        BLSConfig config = ConfigFileReader.getBlsConfig(CONFIG_FILE_NAME);

        // Create our search manager (main webservice class)
        searchManager = new SearchManager(config, true);

        // Set defaults from config in ParameterDefaults
        config.getParameters().setParameterDefaults();

        checkExpectedDebugAddresses(config);

        // Determine default output type.
        defaultOutputType = DataFormat.fromString(searchManager.config().getProtocol().getDefaultOutputType(),
                DataFormat.XML);
    }

    public static void setUpBlsPlugins() {
        // Before the plugin system is initialized, add our plugin type to it
        // and mark plugins as websafe. Opposite order so the list of safe plugins
        // exist when the plugin type is added and the classes are loaded.
        PluginManager.addWebSafePlugins(List.of(
                AuthHttpBasic.class,
                AuthDebugFixed.class,
                AuthClarinEppn.class,
                AuthDebugUrl.class,
                AuthRequestValue.class,
                BcqlParserProvider.class,
                JsonParserProvider.class,
                ContextQLParserProvider.class));
        PluginManager.addPluginType(AuthMethodProvider.class);
    }

    /**
     * Check if localhost addresses are in the list of debug addresses. If not, warn about it.
     * This may help to debug issues in some cases.
     */
    private static void checkExpectedDebugAddresses(BLSConfig config) {
        List<String> addresses = config.getDebug().getAddresses();
        Set<String> missingLocalhosts = new HashSet<>(BLSConfigDebug.DEBUG_ADDRESSES_LOCALHOST);
        for (String address: addresses)
            missingLocalhosts.remove(address);
        if (!missingLocalhosts.isEmpty()) {
            logger.info(
                    "NOTE: debug.addresses has been overridden and no longer contains these expected localhost values: "
                            +
                            StringUtils.join(missingLocalhosts, "; "));
        }
    }

    public DataFormat getDefaultOutputType() {
        return defaultOutputType;
    }

    public SearchManager getSearchManager() {
        return searchManager;
    }

    public synchronized void cleanup() {
        if (searchManager != null) {
            // Stops the load management thread, etc.
            searchManager.cleanup();
            searchManager = null;
        }
    }
}
