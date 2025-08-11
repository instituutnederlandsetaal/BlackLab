package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class ConstraintValueString extends ConstraintValue {

    String value;

    ConstraintValueString(String value) {
        if (value == null)
            throw new IllegalArgumentException("s cannot be null!");
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        ConstraintValueString other = (ConstraintValueString) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueString)
            return value.compareTo(((ConstraintValueString) other).value);
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare string to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return value;
    }

    // TODO: use configured collator for field
    Collators collators = Collators.getDefault();

    public ConstraintValue stringEquals(ConstraintValueString rb, MatchSensitivity sensitivity) {
        return ConstraintValue.get(stringCompareTo(rb, sensitivity) == 0);
    }

    public int stringCompareTo(ConstraintValueString rb, MatchSensitivity sensitivity) {
        return collators.get(sensitivity).compare(getValue(), rb.getValue());
    }
}
