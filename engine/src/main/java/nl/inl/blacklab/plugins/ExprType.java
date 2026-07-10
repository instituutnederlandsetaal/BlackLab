package nl.inl.blacklab.plugins;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;

/**
 * Expression types in BCQL
 */
public enum ExprType {
    /** Any type, including query.
     * If a function parameter is of this type, it must do its own type checking.
     */
    ANY_INCLUDING_QUERY,

    /** Any type except query.
     * If a function parameter is of this type, it must do its own type checking.
     */
    ANY,

    /** No value */
    UNDEFINED,

    /** A corpus query */
    QUERY,

    /** String */
    STRING,

    /** Integer */
    INTEGER,

    /** Integer range, as in e.g. [level=in[3,5]] or <level number=in[3,5] /> */
    INT_RANGE,

    /** True or false */
    BOOLEAN,

    /** A list of values (or vararg) (function must do its own type checking on values) */
    LIST,

    /** Symbol, e.g. Capture group name, annotation name, function name */
    SYMBOL,

    /** Match info, e.g. A in constraint like :: start(A) < 100 */
    MATCH_INFO;

    public static ExprType of(Object o) {
        if (o instanceof ConstraintValue cv)
            return cv.getType();
        if (o instanceof BLSpanQuery)
            return QUERY;
        throw new IllegalArgumentException("Unknown argument type: " + o);
    }

    public static ExprType getWiderType(ExprType type1, ExprType type2) {
        if (type1 == type2)
            return type1;
        // Integer and boolean can be widened to string
        if (type1 == STRING && (type2 == INTEGER || type2 == BOOLEAN)
                || (type1 == INTEGER || type2 == BOOLEAN) && type2 == STRING)
            return STRING;
        // Matchinfo can be widened to integer (will simply take the start position)
        if (type1 == MATCH_INFO && type2 == INTEGER || type1 == INTEGER && type2 == MATCH_INFO)
            return INTEGER;
        return null; // incompatible
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase().replace('_', '-');
    }
}
