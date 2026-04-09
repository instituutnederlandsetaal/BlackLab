package nl.inl.blacklab.search.matchfilter;

import java.util.Objects;

import nl.inl.blacklab.plugins.ExprType;

public class ConstraintValueIntRange extends ConstraintValue {

    final int min;

    final int max;

    public ConstraintValueIntRange(int min, int max) {
        this.min = min;
        this.max = max;
        if (min > max)
            throw new IllegalArgumentException("min > max");
    }

    public Integer[] getValue() {
        return new Integer[]{min, max};
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ConstraintValueIntRange that = (ConstraintValueIntRange) o;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueIntRange) {
            int cmp = Integer.compare(min, ((ConstraintValueIntRange) other).min);
            if (cmp == 0)
                return Integer.compare(max, ((ConstraintValueIntRange) other).max);
            return cmp;
        }
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare int range to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return "in[" + min + "," + max + "]";
    }

    @Override
    public ExprType getType() {
        return ExprType.INT_RANGE;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }
}
