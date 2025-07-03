package nl.inl.blacklab.server.datastream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.lib.results.ApiVersion;

/**
 * Class to stream out XML data.
 *
 * This is faster than building a full object tree first. Intended to replace
 * the DataObject classes.
 */
public class DataStreamXml extends DataStreamAbstract {

    final List<String> tagStack = new ArrayList<>();

    /** Root el document was started with (or null if none) */
    private String rootEl;

    /** Should contextList omit empty annotations if possible? */
    protected boolean omitEmptyAnnotations = false;

    /** Should XML fragments from documents be escaped as CDATA? [true in v5, false before] */
    protected boolean escapeXmlFragment;

    private ApiVersion api;

    @Override
    public void setOmitEmptyAnnotations(boolean omitEmptyAnnotations) {
        this.omitEmptyAnnotations = omitEmptyAnnotations;
    }

    /** Should XML fragments from documents be escaped as CDATA in the XML response?
     *
     * @param escapeXmlFragment true if XML fragments should be escaped
     */
    @Override
    public void setEscapeXmlFragment(boolean escapeXmlFragment) {
        this.escapeXmlFragment = escapeXmlFragment;
    }

    public DataStreamXml(boolean prettyPrint, ApiVersion api) {
        super(prettyPrint);
        setVersion(api);
    }

    @Override
    public void setVersion(ApiVersion version) {
        this.api = version;
        escapeXmlFragment = api.getMajor() >= 5;
    }

    @Override
    public DataStream startOpenEl(String name) {
        tagStack.add(name);
        return indent().print("<").print(name);
    }

    private DataStream attr(String key, String value) {
        return print(" ").print(key).print("=\"").print(StringEscapeUtils.escapeXml10(value)).print("\"");
    }

    @Override
    public DataStream endOpenEl() {
        return print(">").upindent().newline();
    }

    private DataStreamXml openEl(String name) {
        // prevent invalid XML for dynamic elements in old API
        // (e.g. term frequencies, with each term being an element name and the frequency being the value)
        name = AnnotatedFieldNameUtil.sanitizeXmlElementName(name, false);

        startOpenEl(name);
        endOpenEl();
        return this;
    }

    @Override
    public DataStreamXml closeEl() {
        String name = tagStack.remove(tagStack.size() - 1);
        downindent().indent().print("</").print(name).print(">").newline();
        return this;
    }

    @Override
    public void outputProlog() {
        print(XML_PROLOG).newline();
    }

    @Override
    public DataStream startDocument(String rootEl) {
        this.rootEl = rootEl;
        if (rootEl == null)
            return this;
        outputProlog();
        startOpenEl(rootEl);
        return endOpenEl();
    }

    @Override
    public DataStream endDocument() {
        if (rootEl == null)
            return this;
        startCompact();
        return closeEl().endCompact();
    }

    @Override
    public DataStream startList() {
        return this;
    }

    @Override
    public DataStream endList() {
        return this;
    }

    @Override
    public DataStream item(String name, String value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, Object value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, int value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, long value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, double value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, boolean value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream startItem(String name) {
        return openEl(name);
    }

    @Override
    public DataStreamAbstract endItem() {
        return closeEl();
    }

    @Override
    public DataStream startMap() {
        return this;
    }

    @Override
    public DataStream endMap() {
        return this;
    }

    @Override
    public DataStream entry(String key, String value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, Object value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, int value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, long value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, double value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, boolean value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream startEntry(String key) {
        return openEl(key);
    }

    @Override
    public DataStreamAbstract endEntry() {
        return closeEl();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, Object value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, long value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, String value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, int value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, double value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
        if (api.getMajor() >= 5) {
            return elEntry(key, value);
        } else {
            return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                    .endCompact().newline();
        }
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) {
        if (api.getMajor() >= 5) {
            return startElEntry(key);
        } else {
            startOpenEl(elementName);
            attr(attrName, key);
            return endOpenEl();
        }
    }

    @Override
    public DataStream endAttrEntry() {
        if (api.getMajor() >= 5)
            return endElEntry();
        else
            return super.endAttrEntry();
    }

    @Override
    public DataStream elEntry(String key, String value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream elEntry(String key, Object value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream elEntry(String key, int value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream elEntry(String key, long value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream elEntry(String key, double value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream elEntry(String key, boolean value) {
        indent().startCompact();
        super.elEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream startElEntry(String key) {
        openEl("entry");
        indent().startCompact().startEntry("key").value(key).endEntry().endCompact().newline();
        openEl("value");
        return this;
    }

    @Override
    public DataStream endElEntry() {
        return closeEl().closeEl(); // close value and entry elements
    }

    @Override
    public DataStream dynEntry(String key, String value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream dynEntry(String key, Object value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream dynEntry(String key, int value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream dynEntry(String key, long value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream dynEntry(String key, double value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream dynEntry(String key, boolean value) {
        indent().startCompact();
        super.dynEntry(key, value);
        endCompact().newline();
        return this;
    }

    @Override
    public DataStream startDynEntry(String key) {
        return api.getMajor() >= 5 ? this.startElEntry(key) : this.startEntry(key);
    }

    @Override
    public DataStream endDynEntry() {
        return api.getMajor() >= 5 ? this.endElEntry() : this.endEntry();
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values) {
        return api.getMajor() >= 5 ?
                contextListV5(annotations, annotationsToList, values) :
                contextListLegacy(annotations, annotationsToList, values);
    }

    private DataStream contextListV5(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values) {
        startMap();
        int valuesPerWord = annotations.size();
        int numberOfWords = values.size() / valuesPerWord;
        for (int k = 0; k < annotations.size(); k++) {
            if (!annotationsToList.contains(annotations.get(k))) {
                continue;
            }
            Annotation annotation = annotations.get(k);
            startDynEntry(annotation.name()).startList();
            for (int i = 0; i < numberOfWords; i++) {
                int vIndex = i * valuesPerWord;
                String value = values.get(vIndex + k);
                item("value", value);
            }
            endList().endDynEntry();
        }
        endMap();
        return this;
    }

    private DataStreamAbstract contextListLegacy(List<Annotation> annotations,
            Collection<Annotation> annotationsToList, List<String> values) {
        upindent();
        int valuesPerWord = annotations.size();
        int numberOfWords = values.size() / valuesPerWord;
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            indent();
            if (annotationsToList.contains(annotations.get(0))) { // punctuation
                print(StringEscapeUtils.escapeXml10(values.get(vIndex)));
            }
            print("<w");
            for (int k = 1; k < annotations.size() - 1; k++) {
                Annotation annotation = annotations.get(k);
                String value = values.get(vIndex + 1 + j);
                if (annotationsToList.contains(annotation) && (!omitEmptyAnnotations || !value.isEmpty()))
                    print(" ").print(annotation.name()).print("=\"").print(StringEscapeUtils.escapeXml10(value)).print("\"");
                j++;
            }
            print(">");
            print(StringEscapeUtils.escapeXml10(values.get(vIndex + 1 + j)));
            print("</w>");
            newline();
        }
        return downindent();
    }

    @Override
    public DataStreamXml value(String value) {
        indent();
        if (value == null)
            print("(null)");
        else
            print(StringEscapeUtils.escapeXml10(value));
        newline();
        return this;
    }

    @Override
    public DataStream value(long value) {
        return indent().print(value).newline();
    }

    @Override
    public DataStream value(double value) {
        return indent().print(value).newline();
    }

    @Override
    public DataStream value(boolean value) {
        return indent().print(value).newline();
    }

    /**
     * Output an XML fragment, either as a string
     * value (CDATA) or as part of the XML structure.
     *
     * @param fragment
     * @return data stream
     */
    @Override
    public DataStream xmlFragment(String fragment) {
        if (escapeXmlFragment) {
            // In API v5+, we output the fragment as a CDATA section (unquoted or -escaped)
            // (not part of the XML structure, but a string value that may contain XML)
            return cdata(fragment);
        } else {
            // Because we're outputting XML, we output the fragment plain (unquoted or -escaped)
            // (i.e. it becomes part of the XML structure, not a string value)
            return plain(fragment);
        }
    }

    @Override
    public String getType() {
        return "xml";
    }

    private static final String CDATA_START = "<![CDATA[";
    private static final String CDATA_END = "]]>";

    private DataStream cdata(String value) {
        // Escape value for CDATA section (i.e. break into multiple CDATAsections if necessary)
        String escaped = value.replace("]]>", "]]" + CDATA_END + CDATA_START + ">");
        return print(CDATA_START).print(escaped).print(CDATA_END);
    }
}
