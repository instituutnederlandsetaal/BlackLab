package nl.inl.blacklab.server.search;

import java.lang.reflect.Constructor;
import java.util.concurrent.ExecutorService;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabEngine;
import nl.inl.blacklab.searches.SearchCache;
import nl.inl.blacklab.server.auth.AuthManager;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.ConfigurationException;
import nl.inl.blacklab.server.index.IndexManager;

/**
 * Manages the lifetime of a number of objects needed for the web service.
 */
public class SearchManager {

    private static final Logger logger = LogManager.getLogger(SearchManager.class);

    /** Our config */
    private BLSConfig config;

    /** All running searches as well as recently run searches */
    private SearchCache cache;

    /** System for determining the current user. */
    private AuthManager authSystem;

    /** Manages all the indices we have available and/or open */
    private IndexManager indexMan;

    /** Main BlackLab object, containing the search executor service */
    private BlackLabEngine blackLab;

    /**
     *
     * @param config configuration
     * @param forceApplyGlobalConfig if false, only apply global config settings if not previously done
     * @throws ConfigurationException
     */
    public SearchManager(BLSConfig config, boolean forceApplyGlobalConfig) throws ConfigurationException {
        assert config != null;
        this.config = config;

        // load blacklab's internal config before doing anything
        // It's important we do this as early as possible as some things are loaded depending on the config
        // (such as plugins)
        BlackLab.setConfig(config.getBLConfig(), forceApplyGlobalConfig);

        // Create BlackLab instance with the desired number of search threads
        int maxThreadsPerSearch = config.getPerformance().getMaxThreadsPerSearch();
        blackLab = BlackLab.createEngine(maxThreadsPerSearch);

        // Create the cache
        String cacheClass = config.getCache().getImplementation();
        cache = createCache(cacheClass, config, blackLab.searchExecutorService());

        // Find the indices
        indexMan = new IndexManager(this, config);

        // Init auth system
        authSystem = new AuthManager(config.getAuthentication());
    }

    /**
     * Clean up resources.
     *
     * In particular, stops the load manager thread and cancels any running
     * searches.
     */
    public synchronized void cleanup() {

        // Stop any running searches
        cache.cleanup();
        cache = null;

        blackLab.close();
        blackLab = null;

        // Set other variables to null in case it helps GC
        config = null;
        authSystem = null;
        indexMan = null;
    }

    public BlackLabEngine getEngine() {
        return blackLab;
    }

    public SearchCache getBlackLabCache() {
        return cache;
    }

    public BLSConfig config() {
        assert config != null;
        return config;
    }

    public AuthManager getAuthSystem() {
        return authSystem;
    }

    public IndexManager getIndexManager() {
        return indexMan;
    }

    public BlackLabEngine blackLabInstance() {
        return blackLab;
    }

    private SearchCache createCache(String implementationName, BLSConfig config, ExecutorService executorService) {
        // If no implementation is set load the BlsCache as the default implementation
        if (StringUtils.isBlank(implementationName))
            implementationName = "BlsCache";

        // Otherwise load a cache implementation as configured in the settings
        String fqClassName = implementationName.contains(".")
            ? implementationName
            : String.format("nl.inl.blacklab.server.search.%s", implementationName);

        try {
            // Load a class via configuration settings
            // by finding a constructor that matches the below arguments
            // if no constructor is found, the creation of the class fails.
            Constructor<?> declaredConstructor = Class.forName(fqClassName)
                .getDeclaredConstructor(BLSConfig.class, ExecutorService.class);
            SearchCache cache = (SearchCache) declaredConstructor
                .newInstance(config, executorService);
            logger.info("Created cache with class: {}", fqClassName);
            return cache;
        } catch (Exception ex) {
            String message = String.format("Can not create cache with class: %s. Did you check your implementation " +
                "matches the expected arguments", fqClassName);
            logger.error(message, ex);
            throw BlackLabException.wrapRuntime(new ConfigurationException(message));
        }
    }

    public boolean isDebugMode(String originatingAddress) {
        return config().getDebug().isDebugMode(originatingAddress);
    }

}
