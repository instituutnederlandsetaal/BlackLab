package nl.inl.blacklab.search.matchfilter;

public class ConstraintValueUndefined extends ConstraintValue {

    static final ConstraintValue INSTANCE = new ConstraintValueUndefined();

    ConstraintValueUndefined() {
        // NOP
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueUndefined)
            return -1; // undefined is never equal to itself
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare undefined to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public String toString() {
        return "undefined";
    }

}
