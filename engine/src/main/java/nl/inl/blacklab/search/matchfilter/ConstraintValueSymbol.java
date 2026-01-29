package nl.inl.blacklab.search.matchfilter;

import java.util.Objects;

import nl.inl.blacklab.plugins.ExprType;

/** Reference to a match info (capture). */
public class ConstraintValueSymbol extends ConstraintValue {

    String value;

    ConstraintValueSymbol(String value) {
        if (value == null)
            throw new IllegalArgumentException("s cannot be null!");
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConstraintValueSymbol that))
            return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueSymbol)
            return value.compareTo(((ConstraintValueSymbol) other).value);
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare symbol to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public ExprType getType() {
        return ExprType.SYMBOL;
    }

    @Override
    public ConstraintValueString asString() {
        return ConstraintValue.get(value);
    }
}
