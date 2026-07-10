package nl.inl.blacklab.search.matchfilter;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * Data value a constraint (MatchFilter) can evaluate to.
 *
 * e.g. the constraint <code>a.lemma</code> evaluates to a
 * ConstraintValueString while the constraint
 * <code>a.lemma = b.lemma</code> evaluates to a ConstraintValueBoolean.
 */
public abstract class ConstraintValue implements Comparable<ConstraintValue>, TextPattern.EvalResult {

    public static ConstraintValue fromObject(Object o) {
        if (o == null)
            return undefined();
        if (o instanceof ConstraintValue cv)
            return cv;
        if (o instanceof Integer i)
            return get(i);
        if (o instanceof String s)
            return get(s);
        if (o instanceof Boolean b)
            return get(b);
        if (o instanceof Integer[] arr && arr.length == 2)
            return get(arr[0], arr[1]);
        if (o instanceof MatchInfo mi)
            return get(mi);
        if (o instanceof List<?> l)
            return get((List<Object>)l);
        throw new InvalidQuery("Cannot convert object of type " + o.getClass() + " to ConstraintValue");
    }

    public static ConstraintValueInt get(int i) {
        return new ConstraintValueInt(i);
    }

    public static ConstraintValueIntRange get(int min, int max) {
        return new ConstraintValueIntRange(min, max);
    }

    public static ConstraintValueMatchInfo get(MatchInfo mi) {
        return new ConstraintValueMatchInfo(mi);
    }

    public static ConstraintValueString get(String s) {
        return new ConstraintValueString(s);
    }

    public static ConstraintValueBoolean get(boolean b) {
        return b ? ConstraintValueBoolean.TRUE : ConstraintValueBoolean.FALSE;
    }

    public static ConstraintValueSymbol symbol(String symbol) {
        return new ConstraintValueSymbol(symbol);
    }

    public static ConstraintValueList get(List<Object> l) {
        return new ConstraintValueList(l);
    }

    public static ConstraintValue undefined() {
        return ConstraintValueUndefined.INSTANCE;
    }

    public static ConstraintValue convertToType(ConstraintValue ra, ExprType targetType) {
        if (targetType == ra.getType())
            return ra; // no conversion needed
        if (targetType == ExprType.STRING)
            return ra.asString();
        if (targetType == ExprType.INTEGER && ra instanceof ConstraintValueMatchInfo cvmi) {
            // MatchInfo to integer: use the start position
            return ConstraintValue.get(cvmi.matchInfo.getSpanStart());
        }
        throw new InvalidQuery("Cannot convert ConstraintValue of type " + ra.getType() + " to " + targetType);
    }

    public ConstraintValueString asString() {
        throw new InvalidQuery("Cannot convert ConstraintValue of type " + getType() + " to string");
    }

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int compareTo(ConstraintValue rb);

    public abstract boolean isTruthy();

    @Override
    public abstract String toString();

    public abstract Object getValue();

    public abstract ExprType getType();
}
