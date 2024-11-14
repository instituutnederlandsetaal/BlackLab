package nl.inl.blacklab.search.textpattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

    public enum Adjust {
        FULL_TAG,
        LEADING_EDGE,
        TRAILING_EDGE;

        public static Adjust fromString(String s) {
            if (s == null || s.isEmpty())
                return FULL_TAG;
            switch (s.toLowerCase()) {
            case "full_tag":
                return FULL_TAG;
            case "leading_edge":
                return LEADING_EDGE;
            case "trailing_edge":
                return TRAILING_EDGE;
            default:
                throw new IllegalArgumentException("Unknown adjust value: " + s);
            }
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private final String elementName;

    private final Map<String, MatchValue> attributes;

    private final Adjust adjust;

    private final String captureAs;

    public TextPatternTags(String elementName, Map<String, MatchValue> attributes) {
        this(elementName, attributes, Adjust.FULL_TAG, "");
    }

    public TextPatternTags(String elementName, Map<String, MatchValue> attributes, Adjust adjust, String captureAs) {
        boolean desensitize = !BlackLab.config().getIndexing().isRelationsSensitive();
        if (desensitize) {
            // By default, tags are now indexed insensitive
            // (lowercased and stripped of diacritics); desensitize the search terms as well
            this.elementName = StringUtil.desensitize(elementName);
            Map<String, MatchValue> desensitized = Collections.emptyMap();
            if (attributes != null) {
                desensitized = new HashMap<>();
                for (Map.Entry<String, MatchValue> e: attributes.entrySet()) {
                    desensitized.put(StringUtil.desensitize(e.getKey()), e.getValue().desensitize());
                }
            }
            this.attributes = desensitized;
        } else {
            // BlackLab has been configured to index tags sensitive (the previous default);
            // don't desensitize the search terms
            this.elementName = elementName;
            this.attributes = attributes == null ? Collections.emptyMap() : attributes;
        }
        this.adjust = adjust == null ? Adjust.FULL_TAG : adjust;
        this.captureAs = captureAs == null ? "" : captureAs;
    }

    public TextPatternTags withCapture(String captureAs) {
        return new TextPatternTags(elementName, attributes, Adjust.FULL_TAG, captureAs);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        // Desensitize tag name and attribute values if required
        context = context.withRelationAnnotation();
        String optInsensitiveElName = optInsensitive(context, elementName);
        Map<String, String> attrOptIns = new HashMap<>();
        for (Map.Entry<String, MatchValue> e : attributes.entrySet()) {
            attrOptIns.put(e.getKey(), optInsensitive(context, e.getValue().getRegex()));
        }

        // Use element name if no explicit name given. Keep only characters and add unique number if needed.
        String captureAsOrAuto = captureAs;
        if (StringUtils.isEmpty(captureAsOrAuto)) {
            String name = elementName.isEmpty() ? "span" : elementName;
            name = name.replaceAll("[^\\p{L}]", "");
            captureAsOrAuto = context.ensureUniqueCapture(name);
        }

        // Return the proper SpanQuery depending on index version
        return context.index().tagQuery(context.queryInfo(), context.luceneField(),
                optInsensitiveElName, attrOptIns, adjust, captureAsOrAuto);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternTags that = (TextPatternTags) o;
        return Objects.equals(elementName, that.elementName) && Objects.equals(attributes,
                that.attributes) && adjust == that.adjust && Objects.equals(captureAs, that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementName, attributes, adjust, captureAs);
    }

    @Override
    public String toString() {
        String optAttr = attributes != null && !attributes.isEmpty() ? ", " + attributes : "";
        String optAdjust = adjust != Adjust.FULL_TAG ? ", " + adjust : "";
        String optCapture = !captureAs.isEmpty() ? ", " + captureAs : "";
        return "TAGS(" + elementName + optAttr + optAdjust + optCapture + ")";
    }

    public String getElementName() {
        return elementName;
    }

    public Map<String, MatchValue> getAttributes() {
        return attributes;
    }

    public String getCaptureAs() {
        return captureAs;
    }

    public Adjust getAdjust() {
        return adjust;
    }
}
