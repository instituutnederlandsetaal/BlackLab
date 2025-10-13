package nl.inl.blacklab.search.matchfilter;

/**
 * Data value a constraint (MatchFilter) can evaluate to.
 *
 * e.g. the constraint <code>a.lemma</code> evaluates to a
 * ConstraintValueString while the constraint
 * <code>a.lemma = b.lemma</code> evaluates to a ConstraintValueBoolean.
 */
public abstract class ConstraintValue implements Comparable<ConstraintValue> {

    public static ConstraintValue get(int i) {
        return new ConstraintValueInt(i);
    }

    public static ConstraintValue get(String s) {
        return new ConstraintValueString(s);
    }

    public static ConstraintValue get(boolean b) {
        return b ? ConstraintValueBoolean.TRUE : ConstraintValueBoolean.FALSE;
    }

    public static ConstraintValue undefined() {
        return ConstraintValueUndefined.INSTANCE;
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
}
