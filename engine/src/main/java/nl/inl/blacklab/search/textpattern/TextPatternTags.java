package nl.inl.blacklab.search.textpattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueIntRange;
import nl.inl.util.StringUtil;

/**
 * A TextPattern matching a word.
 */
public class TextPatternTags extends TextPattern {

    public static int TP_PRECEDENCE = 6;

    public enum Adjust {
        FULL_TAG,
        LEADING_EDGE,
        TRAILING_EDGE;

        public static Adjust fromString(String s) {
            if (s == null || s.isEmpty())
                return FULL_TAG;
            return switch (s.toLowerCase()) {
                case "full_tag" -> FULL_TAG;
                case "leading_edge" -> LEADING_EDGE;
                case "trailing_edge" -> TRAILING_EDGE;
                default -> throw new IllegalArgumentException("Unknown adjust value: " + s);
            };
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private final String elementNameRegex;

    private final Map<String, TextPattern> attributes;

    private final Adjust adjust;

    private final String captureAs;

    public TextPatternTags(String elementNameRegex, Map<String, TextPattern> attributes) {
        this(elementNameRegex, attributes, Adjust.FULL_TAG, "");
    }

    public TextPatternTags(String elementNameRegex, Map<String, TextPattern> attributes, Adjust adjust, String captureAs) {
        super(TP_PRECEDENCE);
        this.elementNameRegex = elementNameRegex;
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
        this.adjust = adjust == null ? Adjust.FULL_TAG : adjust;
        this.captureAs = captureAs == null ? "" : captureAs;
    }

    public TextPatternTags withCapture(String captureAs) {
        return new TextPatternTags(elementNameRegex, attributes, Adjust.FULL_TAG, captureAs);
    }

    @Override
    public EvalResult evaluate(QueryExecutionContext context) {
        // Desensitize tag name and attribute values if required
        context = context.withRelationAnnotation();
        String optDesensitizedElNameRegex = context.optDesensitize(elementNameRegex);
        Map<String, String> attrOptIns = new HashMap<>();
        for (Map.Entry<String, TextPattern> e : attributes.entrySet()) {
            TextPattern tp = e.getValue();
            EvalResult o = tp.evaluate(context);
            String regex;
            if (o instanceof ConstraintValue cvs) {
                if (o instanceof ConstraintValueIntRange cvir)
                    regex = TextPatternCompare.regexForRange(cvir.getMin(), cvir.getMax());
                else
                    regex = cvs.asString().getValue();
            } else {
                throw new IllegalArgumentException("Attribute value must evaluate to a string or int range");
            }
            attrOptIns.put(e.getKey(), context.optDesensitize(regex));
        }

        // Use element name if no explicit name given. Keep only characters and add unique number if needed.
        String captureAsOrAuto = captureAs;
        if (StringUtils.isEmpty(captureAsOrAuto)) {
            String name = elementNameRegex.isEmpty() ? "span" : StringUtil.sanitizeCaptureName(elementNameRegex);
            name = name.replaceAll("[^\\p{L}]", "");
            captureAsOrAuto = context.ensureUniqueCapture(name);
        }

        // Return the proper SpanQuery depending on index version
        return context.index().tagQuery(context.queryInfo(), context.luceneFieldRef(),
                optDesensitizedElNameRegex, attrOptIns, adjust, captureAsOrAuto);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternTags that = (TextPatternTags) o;
        return Objects.equals(elementNameRegex, that.elementNameRegex) && Objects.equals(attributes,
                that.attributes) && adjust == that.adjust && Objects.equals(captureAs, that.captureAs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementNameRegex, attributes, adjust, captureAs);
    }

    @Override
    public String toString() {
        String optAttr = attributes != null && !attributes.isEmpty() ? ", " + attributes : "";
        String optAdjust = adjust != Adjust.FULL_TAG ? ", " + adjust : "";
        String optCapture = !captureAs.isEmpty() ? ", " + captureAs : "";
        return "TAGS(" + elementNameRegex + optAttr + optAdjust + optCapture + ")";
    }

    public String getElementNameRegex() {
        return elementNameRegex;
    }

    public Map<String, TextPattern> getAttributes() {
        return attributes;
    }

    public String getCaptureAs() {
        return captureAs;
    }

    public Adjust getAdjust() {
        return adjust;
    }

    @Override
    public <T> T accept(TextPatternVisitor<T> visitor) {
        return visitor.visitTags(this);
    }
}
