package org.ivdnt.blacklab.proxy;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.ivdnt.blacklab.proxy.helper.ErrorReadingConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ProxyConfig {

    private static ProxyConfig instance;

    public static ProxyConfig get() {
        if (instance == null)
            instance = readConfig();
        return instance;
    }

    /** Server to reverse proxy for */
    public static class ProxyTarget {

        /** URL to proxy to */
        private String url;

        /** What protocol does the proxy target speak? (for now, "BLS" or "Solr") */
        private String protocol;

        private String defaultCorpusName = "";

        public String getUrl() {
            return url;
        }

        public String getProtocol() {
            return protocol;
        }

        public String getDefaultCorpusName() {
            return defaultCorpusName;
        }
    }

    private ProxyTarget proxyTarget;

    static ProxyConfig readConfig() {
        File configFile = locateConfigFile();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(configFile, ProxyConfig.class);
        } catch (IOException e) {
            throw new ErrorReadingConfig("Error reading proxy config " + configFile, e);
        }
    }

    private static File locateConfigFile() {
        String path = System.getenv("BLACKLAB_PROXY_CONFIG_FILE");
        File configFile;
        if (StringUtils.isEmpty(path)) {
            configFile = new File(System.getProperty("user.home") + "/.blacklab/proxy.yaml");
            if (!configFile.exists())
                configFile = new File("/etc/blacklab/proxy.yaml");
            if (!configFile.exists())
                throw new ErrorReadingConfig("No config file found in $BLACKLAB_PROXY_CONFIG_FILE, ~/.blacklab/proxy.yaml, or /etc/blacklab/proxy.yaml");
        } else
            configFile = new File(path);
        return configFile;
    }

    private ProxyConfig() {}

    public ProxyTarget getProxyTarget() {
        return proxyTarget;
    }
}
