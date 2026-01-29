package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.plugins.ExprType;

public class ConstraintValueInt extends ConstraintValue {

    final int i;

    ConstraintValueInt(int i) {
        this.i = i;
    }

    public Integer getValue() {
        return i;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + i;
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
        ConstraintValueInt other = (ConstraintValueInt) obj;
        return i == other.i;
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueInt)
            return Integer.compare(i, ((ConstraintValueInt) other).i);
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare int to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return Integer.toString(i);
    }

    @Override
    public ExprType getType() {
        return ExprType.INTEGER;
    }

    @Override
    public ConstraintValueString asString() {
        return new ConstraintValueString(Integer.toString(i));
    }

}
