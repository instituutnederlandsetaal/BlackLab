package org.ivdnt.blacklab.proxy.helper;

import java.io.IOException;
import java.io.StringWriter;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import nl.inl.util.Json;

/** In the XML, write a JSON structure as a string. Used for summary.pattern.json. */
public class JsonAsString extends XmlAdapter<Object, Object> {

    @Override
    public String marshal(Object o) {
        // Writing JSON in the XML response; just stringify it
        StringWriter sw = new StringWriter();
        if (o != null) {
            try {
                Json.getJaxbWriter().writeValue(sw, o);
            } catch (IOException e) {
                throw new ErrorWritingResponse(e);
            }
        }
        return sw.toString();
    }

    @Override
    public Object unmarshal(Object v) {
        if (v instanceof String)
            throw new UnsupportedOperationException("Proxy cannot read XML, only write it");
        // Already a JSON structure; pass it through unmoddified
        return v;
    }
}
