package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureGroup;

/**
 * TextPattern for capturing a subclause as a named "group".
 */
public class TextPatternCaptureGroup extends TextPattern {

    public static TextPattern get(TextPattern input, String captureAs) {
        if (input instanceof TextPatternTags) {
            return ((TextPatternTags)input).withCapture(captureAs);
        }
        return new TextPatternCaptureGroup(input, captureAs);
    }

    private final TextPattern clause;

    private final String captureAs;

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param clause the clause to tag with this name
     * @param captureAs the tag name
     */
    public TextPatternCaptureGroup(TextPattern clause, String captureAs) {
        this.clause = clause;
        this.captureAs = captureAs;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return new SpanQueryCaptureGroup(clause.translate(context), captureAs, 0, 0, null);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((captureAs == null) ? 0 : captureAs.hashCode());
        result = prime * result + ((clause == null) ? 0 : clause.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternCaptureGroup other = (TextPatternCaptureGroup) obj;
        if (captureAs == null) {
            if (other.captureAs != null)
                return false;
        } else if (!captureAs.equals(other.captureAs))
            return false;
        if (clause == null) {
            if (other.clause != null)
                return false;
        } else if (!clause.equals(other.clause))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "CAPTURE(" + clause.toString() + ", " + captureAs + ")";
    }

    public String getCaptureName() {
        return captureAs;
    }

    public TextPattern getClause() {
        return clause;
    }

    @Override
    public boolean isRelationsQuery() {
        return clause.isRelationsQuery();
    }
}
