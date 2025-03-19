package nl.inl.blacklab.indexers.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configuration for a processing step on a value. */
public class ConfigProcessStep {

    /** Method to call */
    private String method;

    /** Extra parameters to pass */
    private final Map<String, Object> param = new LinkedHashMap<>();

    public void validate() {
        String t = "processing step";
        ConfigInputFormat.req(method, t, "method");
    }

    public ConfigProcessStep copy() {
        ConfigProcessStep cp = new ConfigProcessStep();
        cp.setMethod(method);
        cp.param.putAll(param);
        return cp;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParam() {
        return param;
    }

    public void addParam(String name, Object value) {
        this.param.put(name, value);
    }

    @Override
    public String toString() {
        return "ConfigProcessStep [method=" + method + ", param=" + param + "]";
    }
    
}
