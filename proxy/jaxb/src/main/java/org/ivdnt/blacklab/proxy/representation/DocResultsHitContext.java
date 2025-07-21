package org.ivdnt.blacklab.proxy.representation;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

/**
 * Match inside doc for doc results.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class DocResultsHitContext {

    public ContextWords left;

    public ContextWords match;

    public ContextWords right;

    // required for Jersey
    public DocResultsHitContext() {}

    @Override
    public String toString() {
        return "DocSnippet{" +
                ", left=" + left +
                ", match=" + match +
                ", right=" + right +
                '}';
    }
}
