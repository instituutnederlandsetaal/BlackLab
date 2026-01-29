package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.plugins.ExprType;

public class ConstraintValueBoolean extends ConstraintValue {

    public static final ConstraintValueBoolean FALSE = new ConstraintValueBoolean(false);

    public static final ConstraintValueBoolean TRUE = new ConstraintValueBoolean(true);

    private final boolean b;

    public ConstraintValueBoolean(boolean b) {
        this.b = b;
    }

    @Override
    public boolean isTruthy() {
        return b;
    }

    @Override
    public String toString() {
        return b ? "true" : "false";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (b ? 1231 : 1237);
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
        ConstraintValueBoolean other = (ConstraintValueBoolean) obj;
        return b == other.b;
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueBoolean)
            return Boolean.compare(b, ((ConstraintValueBoolean) other).b);
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare bool to " + other.getClass().getName());
    }

    @Override
    public Boolean getValue() {
        return b;
    }

    @Override
    public ExprType getType() {
        return ExprType.BOOLEAN;
    }

    @Override
    public ConstraintValueString asString() {
        return new ConstraintValueString(Boolean.toString(b));
    }
}
