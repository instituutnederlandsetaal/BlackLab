package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.util.WebserviceUtil;
import nl.inl.util.Json;

public interface DataStream {
    String XML_PROLOG = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>";

    /** Set the API version. Determines the structure of some responses. */
    default void setVersion(ApiVersion version) {
        // do nothing by default (XML overrides)
    }

    /**
     * Construct a simple status response object.
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg  the message
     */
    default void statusObject(String code, String msg) {
        startMap()
                .startEntry("status")
                .startMap()
                .entry("code", code)
                .entry("message", msg);
        endMap()
                .endEntry()
                .endMap();
    }

    /**
     * Construct a simple error response object.
     *
     * @param code (string) error code
     * @param msg  the error message
     * @param e    if specified, include stack trace
     */
    default void error(String code, String msg, Map<String, String> info, Throwable e) {
        startMap()
                .startEntry("error")
                .startMap()
                .entry("code", code)
                .entry("message", msg)
                .entry("info", info == null ? Collections.emptyMap() : info);
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            entry("stackTrace", sw.toString());
        }
        endMap()
                .endEntry()
                .endMap();
    }

    /**
     * Construct a simple error response object.
     *
     * @param code (string) error code
     * @param msg  the error message
     */
    default void error(String code, String msg, Map<String, String> info) {
        error(code, msg, info, null);
    }

    default void internalError(Exception e, boolean debugMode, String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(e, debugMode, code), null, debugMode ? e : null);
    }

    default void internalError(String message, boolean debugMode, String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(message, debugMode, code), null);
    }

    default void internalError(String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(code), null);
    }

    DataStream endCompact();

    DataStream startCompact();

    void outputProlog();

    DataStream startDocument(String rootEl);

    DataStream endDocument();

    DataStream startList();

    DataStream endList();

    DataStream startItem(String name);

    DataStream endItem();

    default DataStream item(String name, String value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, Object value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, int value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, long value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, double value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, boolean value) {
        return startItem(name).value(value).endItem();
    }

    DataStream startMap();

    DataStream endMap();

    DataStream startEntry(String key);

    DataStream endEntry();

    default DataStream entry(String key, String value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, Object value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, int value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, long value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, double value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, boolean value) {
        return startEntry(key).value(value).endEntry();
    }

    DataStream startAttrEntry(String elementName, String attrName, String key);

    default DataStream endAttrEntry() { return endEntry(); }

    default DataStream attrEntry(String elementName, String attrName, String key, String value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, Object value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, int value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, long value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, double value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    /** Start map entry with dynamic key.
     *
     *  XML format depends on the API version.
     *  For API v5+, entry/key/value elements will be used in XML.
     *  Replaces startEntry() for dynamic keys, to avoid illegal element names.
     */
    default DataStream startDynEntry(String key) {
        return startEntry(key);
    }

    /** End dynamic key map entry. */
    default DataStream endDynEntry() {
        return endEntry();
    }

    default DataStream dynEntry(String key, String value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    default DataStream dynEntry(String key, Object value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    default DataStream dynEntry(String key, int value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    default DataStream dynEntry(String key, long value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    default DataStream dynEntry(String key, double value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    default DataStream dynEntry(String key, boolean value) {
        return startDynEntry(key).value(value).endDynEntry();
    }

    /** An entry that will be rendered using separate elements in XML (default JAXB serialization) */
    default DataStream startElEntry(String key) {
        return startEntry(key);
    }

    default DataStream endElEntry() {
        return endEntry();
    }

    default DataStream elEntry(String key, String value) {
        return startElEntry(key).value(value).endElEntry();
    }

    default DataStream elEntry(String key, Object value) {
        return startElEntry(key).value(value).endElEntry();
    }

    default DataStream elEntry(String key, int value) {
        return startElEntry(key).value(value).endElEntry();
    }

    default DataStream elEntry(String key, long value) {
        return startElEntry(key).value(value).endElEntry();
    }

    default DataStream elEntry(String key, double value) {
        return startElEntry(key).value(value).endElEntry();
    }

    default DataStream elEntry(String key, boolean value) {
        return startElEntry(key).value(value).endElEntry();
    }

    DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values);

    default DataStream value(TextPattern pattern) {
        try {
            // By default, write it as a string (only JSON "properly" incorporates it into the response).
            return value(Json.getJaxbWriter().writeValueAsString(pattern));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    DataStream value(String value);

    DataStream value(long value);

    DataStream value(double value);

    DataStream value(boolean value);

    /**
     * Output a map.
     * May contain nested structures (Map, List) and/or values.
     *
     * @param value map to output
     * @param <S>   entry key type
     * @param <T>   entry value type
     * @return this data stream
     */
    default <S, T> DataStream value(Map<S, T> value) {
        startMap();
        if (value != null) {
            for (Map.Entry<S, T> entry: value.entrySet()) {
                startDynEntry(entry.getKey().toString()).value(entry.getValue()).endDynEntry();
            }
        }
        endMap();
        return this;
    }

    /**
     * Output a list.
     * May contain nested structures (Map, List) and/or values.
     * Uses "item" for the list item name (in XML mode).
     *
     * @param value list to output
     * @param <T>   list item type
     * @return this data stream
     */
    default <T> DataStream value(List<T> value) {
        startList();
        if (value != null) {
            for (T item: value) {
                startItem("item").value(item).endItem();
            }
        }
        endList();
        return this;
    }

    /**
     * Output a value that may be a nested structure (Map or List) or simple value.
     *
     * @param value value to output
     * @return this data stream
     */
    default DataStream value(Object value) {
        if (value instanceof TextPattern) {
            return value((TextPattern) value);
        } else if (value instanceof Map) {
            return value((Map) value);
        } else if (value instanceof List) {
            return value((List) value);
        } else if (value instanceof String) {
            return value((String) value);
        } else if (value instanceof Integer || value instanceof Long) {
            return value(((Number) value).longValue());
        } else if (value instanceof Double || value instanceof Float) {
            return value(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            return value((boolean) value);
        } else {
            return value(value == null ? "" : value.toString());
        }
    }

    DataStream plain(String value);

    /**
     * Should contextList omit empty annotations if possible? (XML only)
     */
    default void setOmitEmptyAnnotations(boolean omitEmptyAnnotations) { /* do nothing */ }

    /** Should XML fragments be CDATA in the XML response?
     *
     * @param b true if XML fragments should be encoded as CDATA (the default)
     */
    default void setEscapeXmlFragment(boolean b) { /* do nothing */ }

    DataStream xmlFragment(String fragment);

    default <T> void list(String itemName, T[] items) {
        startList();
        for (T i: items) {
            item(itemName, i);
        }
        endList();
    }

    default <T> void list(String itemName, Iterable<T> items) {
        startList();
        for (T i: items) {
            item(itemName, i);
        }
        endList();
    }

    String getOutput();

    int length();

    DataStream newline();

    DataStream space();

    /**
     * Output a full CSV document. Subclasses may choose to embed it in their response format.
     */
    default void csv(String csv) {
        plain(csv);
    }

    /**
     * Output a full XSLT document. Subclasses may choose to embed it in their response format.
     */
    default void xslt(String xslt) {
        plain(xslt);
    }

    /** Needed to return parts of the XML document, wrapped in an extra root element and optional namespaces. */
    default DataStream startOpenEl(String name) {
        throw new UnsupportedOperationException();
    }

    /** Needed to return parts of the XML document, wrapped in an extra root element and optional namespaces. */
    default DataStream endOpenEl() {
        throw new UnsupportedOperationException();
    }

    /** Needed to return parts of the XML document, wrapped in an extra root element and optional namespaces. */
    default DataStream closeEl() {
        throw new UnsupportedOperationException();
    }

    /** Type of data stream we're writing (json/xml/csv).
     *
     * (DataStreamSolr also says 'json')
     */
    String getType();
}
