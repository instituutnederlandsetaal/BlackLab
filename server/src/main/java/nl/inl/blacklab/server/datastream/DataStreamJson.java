package nl.inl.blacklab.server.datastream;

import java.util.Collection;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.Json;

/**
 * Class to stream out JSON data.
 *
 * This is faster than building a full object tree first. Intended to replace
 * the DataObject classes.
 */
public class DataStreamJson extends DataStreamAbstract {

    /** First entry in map/list: don't print separator */
    boolean firstEntry = true;

    public DataStreamJson(boolean prettyPrint) {
        super(prettyPrint);
    }

    DataStream openbl(String str) {
        firstEntry = true;
        return print(str).upindent();
    }

    DataStream closebl(String str) {
        firstEntry = false;
        return downindent().newlineIndent().print(str);
    }

    @Override
    public DataStream startDocument(String rootEl) {
        return this;
    }

    @Override
    public DataStream endDocument() {
        return this;
    }

    @Override
    public DataStream startList() {
        return openbl("[");
    }

    @Override
    public DataStream endList() {
        return closebl("]");
    }

    @Override
    public DataStream startItem(String name) {
        return optSep().newlineIndent();
    }

    @Override
    public DataStreamAbstract endItem() {
        return this;
    }

    @Override
    public DataStream startMap() {
        return openbl("{");
    }

    @Override
    public DataStream endMap() {
        return closebl("}");
    }

    DataStreamAbstract optSep() {
        if (!firstEntry)
            return print(",");
        firstEntry = false;
        return this;
    }

    @Override
    public DataStream startEntry(String key) {
        return optSep().newlineIndent().print("\"").print(StringEscapeUtils.escapeJson(key)).print("\":").space();
    }

    @Override
    public DataStreamAbstract endEntry() {
        return this;
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) {
        return startEntry(key);
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values) {
        openbl("{");
        int valuesPerWord = annotations.size();
        int numberOfWords = values.size() / valuesPerWord;
        for (int k = 0; k < annotations.size(); k++) {
            if (!annotationsToList.contains(annotations.get(k))) {
                continue;
            }

            optSep();
            newlineIndent();
            Annotation annotation = annotations.get(k);
            print("\"").print(StringEscapeUtils.escapeJson(annotation.name())).print("\":[");
            for (int i = 0; i < numberOfWords; i++) {
                if (i > 0)
                    print(",");
                int vIndex = i * valuesPerWord;
                String value = values.get(vIndex + k);
                print("\"").print(StringEscapeUtils.escapeJson(value)).print("\"");
            }
            out.append("]");
        }
        return closebl("}");
    }

    @Override
    public DataStream value(TextPattern pattern) {
        try {
            return print(Json.getJaxbWriter().writeValueAsString(pattern));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public DataStream value(String value) {
        return value == null ? print("null") : print("\"").print(StringEscapeUtils.escapeJson(value)).print("\"");
    }

    @Override
    public DataStream value(long value) {
        return print(value);
    }

    @Override
    public DataStream value(double value) {
        return print(value);
    }

    @Override
    public DataStream value(boolean value) {
        return print(value);
    }

    @Override
    public String getType() {
        return "json";
    }

}
