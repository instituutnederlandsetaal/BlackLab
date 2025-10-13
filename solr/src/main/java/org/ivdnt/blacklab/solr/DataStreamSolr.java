package org.ivdnt.blacklab.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.response.SolrQueryResponse;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.textpattern.MatchValue;
import nl.inl.blacklab.search.textpattern.MatchValueIntRange;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerCql;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerJson;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.util.ObjectSerializationWriter;

public class DataStreamSolr implements DataStream {

    private final SolrQueryResponse rsp;

    /** Parent objects to the current object we're writing. */
    private final List<Object> parents = new ArrayList<>();

    /** Current NamedList or List we're writing. */
    private Object currentObject = null;

    /** The key for which we're about to write a value (if currentObject is a NamedList) */
    private String currentKey = null;

    public DataStreamSolr(SolrQueryResponse rsp) {
        this.rsp = rsp;
    }

    @Override
    public String getOutput() {
        throw new UnsupportedOperationException("Not implemented for Solr");
    }

    @Override
    public int length() {
        throw new UnsupportedOperationException("Not implemented for Solr");
    }

    @Override
    public DataStream endCompact() {
        return this;
    }

    @Override
    public DataStream startCompact() {
        return null;
    }

    @Override
    public void outputProlog() {
        // NOP
    }

    @Override
    public DataStream startDocument(String rootEl) {
        startStructure(new SimpleOrderedMap<>());
        return this;
    }

    @Override
    public DataStream endDocument() {
        if (currentObject != null) {
            Object v = currentObject;
            ensureMap();
            endStructure();
            NamedList<Object> nl = (NamedList<Object>)v;
            nl.forEach(rsp::add);
        }
        return this;
    }

    @Override
    public DataStream startList() {
        startStructure(new ArrayList<>());
        return this;
    }

    /** Start a list or object as a substructure of the current structure
     *  (i.e. a list item or an object entry value)
     * @param l structure we're starting (must be a List or a NamedList)
     */
    private void startStructure(Object l) {
        if (currentObject != null) {
            addValueToCurrentStructure(l);
            parents.add(currentObject);
        }
        currentObject = l;
    }

    private void addValueToCurrentStructure(Object l) {
        if (currentObject instanceof List) {
            // List item
            ((List) currentObject).add(l);
        } else if (currentObject instanceof NamedList) {
            // Object entry (not top-level)
            if (currentKey == null) {
                throw new IllegalStateException("No key set when adding value: " + l);
            } else {
                ((NamedList) currentObject).add(currentKey, l);
                currentKey = null;
            }
        } else if (currentObject == null && parents.isEmpty()) {
            // Top-level entry
            if (currentKey == null) {
                throw new IllegalStateException("No key set when adding value: " + l);
            } else {
                rsp.add(currentKey, l);
                currentKey = null;
            }
        } else if (currentObject == null) {
            throw new IllegalStateException("No current object");
        } else {
            throw new IllegalStateException(
                    "Current object not a List or NamedList: " + currentObject.getClass().getName());
        }
    }

    private void ensureList() {
        if (!(currentObject instanceof List))
            throw new IllegalStateException("Current object is not a list");
    }

    private void ensureMap() {
        if (!(currentObject instanceof NamedList))
            throw new IllegalStateException("Current object is not a map");
    }

    private void endStructure() {
        if (currentObject == null)
            throw new IllegalStateException("No structure opened");
        if (parents.isEmpty())
            currentObject = null;
        else
            currentObject = parents.remove(parents.size() - 1);
    }

    @Override
    public DataStream endList() {
        ensureList();
        endStructure();
        return this;
    }

    @Override
    public DataStream startItem(String name) {
        ensureList();
        return this;
    }

    @Override
    public DataStream endItem() {
        ensureList();
        return this;
    }

    @Override
    public DataStream startMap() {
        startStructure(new SimpleOrderedMap<>());
        return this;
    }

    @Override
    public DataStream endMap() {
        ensureMap();
        endStructure();
        return this;
    }

    @Override
    public DataStream startEntry(String key) {
        currentKey = key;
        return this;
    }

    @Override
    public DataStream endEntry() {
        return this;
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) {
        return startEntry(key);
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList,
            List<String> values) {
        startMap();
        {
            int valuesPerWord = annotations.size();
            int numberOfWords = values.size() / valuesPerWord;
            for (int k = 0; k < annotations.size(); k++) {
                Annotation annotation = annotations.get(k);
                if (!annotationsToList.contains(annotation))
                    continue;
                startEntry(annotation.name()).startList();
                {
                    for (int i = 0; i < numberOfWords; i++) {
                        int vIndex = i * valuesPerWord;
                        value(values.get(vIndex + k));
                    }
                }
                endList().endEntry();
            }
        }
        return endMap();
    }

    @Override
    public DataStream value(String value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(long value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(double value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(boolean value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(TextPattern pattern) {
        TextPatternSerializerJson.serialize(pattern, (type, args) -> {
            startMap();
            entry("bcqlFragment", TextPatternSerializerCql.serialize(pattern));
            entry("type", type);
            Map<String, Object> map = ObjectSerializationWriter.mapFromArgs(args);
            for (Map.Entry<String, Object> e: map.entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();
                if (value != null) {
                    if (key.equals(TextPatternSerializerJson.KEY_ATTRIBUTES)) {
                        // Attributes in "tags" node. Special case because match values can now be an int range
                        // as well as (the more common) regex.
                        // (we could have made MatchValue a TextPatternStruct, but that would change
                        //  the JSON structure (each attribute value would be a JSON object with a type).
                        //  We want to keep everything as-is while also adding the int range filter option)
                        startEntry(key).startMap();
                        Map<String, MatchValue> attributes = (Map<String, MatchValue>) value;
                        for (Map.Entry<String, MatchValue> attr: attributes.entrySet()) {
                            startEntry(attr.getKey());
                            if (attr.getValue() instanceof MatchValueIntRange range) {
                                // Int range; serialize as an object with min and max fields
                                startMap();
                                entry(TextPatternSerializerJson.KEY_MIN, range.min());
                                entry(TextPatternSerializerJson.KEY_MAX, range.max());
                                endMap();
                            } else {
                                // Regex; serialize as a simple string (as we have always done)
                                value(attr.getValue().regex());
                            }
                            endEntry();
                        }
                        endMap().endEntry();
                    } else {
                        entry(key, value);
                    }
                }
            }
            endMap();
        });
        return this;
//        try {
//            return print(Json.getJaxbWriter().writeValueAsString(pattern));
//        } catch (JsonProcessingException e) {
//            throw new IllegalStateException(e);
//        }
    }

    @Override
    public DataStream plain(String value) {
        throw new UnsupportedOperationException("Not implemented for Solr");
    }

    @Override
    public DataStream xmlFragment(String fragment) {
        value(fragment);
        return this;
    }

    @Override
    public DataStream space() {
        return this;
    }

    @Override
    public DataStream newline() {
        return this;
    }

    @Override
    public void csv(String csv) {
        // Solr doesn't support custom CSV output, so we'll wrap it in a field,
        // which the client should easily be able to extract.
        startMap().entry("csv", csv).endMap();
    }

    @Override
    public void xslt(String xslt) {
        // Solr doesn't easily support custom output formats, so we'll wrap it in a field,
        // which the client should easily be able to extract.
        startMap().entry("xslt", xslt).endMap();
    }

    @Override
    public String getType() {
        return "json";
    }
}
