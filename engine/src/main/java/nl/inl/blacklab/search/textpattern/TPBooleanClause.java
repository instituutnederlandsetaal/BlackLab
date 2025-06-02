package nl.inl.blacklab.search.textpattern;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * Adapted from Lucene's BooleanClause. Manages a TextPattern clause and an
 * Occur setting.
 */
public class TPBooleanClause {

    /**
     * The query whose matching documents are combined by the boolean query.
     */
    private TextPattern query;

    private Occur occur;

    /**
     * Constructs a TPBooleanClause.
     * 
     * @param query the clause
     * @param occur if the clause should, must or must not occur
     */
    public TPBooleanClause(TextPattern query, Occur occur) {
        this.query = query;
        this.occur = occur;
    }

    public Occur getOccur() {
        return occur;
    }

    public void setOccur(Occur occur) {
        this.occur = occur;

    }

    public TextPattern getQuery() {
        return query;
    }

    public void setQuery(TextPattern query) {
        this.query = query;
    }

    public boolean isProhibited() {
        return Occur.MUST_NOT == occur;
    }

    public boolean isRequired() {
        return Occur.MUST == occur;
    }

    /** Returns true if <code>o</code> is equal to this. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TPBooleanClause other))
            return false;
        return query.equals(other.query) && occur == other.occur;
    }

    /** Returns a hash code value for this object. */
    @Override
    public int hashCode() {
        return query.hashCode() ^ (Occur.MUST == occur ? 1 : 0) ^ (Occur.MUST_NOT == occur ? 2 : 0);
    }

    @Override
    public String toString() {
        return occur.toString() + query.toString();
    }

}
