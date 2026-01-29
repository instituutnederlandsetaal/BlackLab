package nl.inl.blacklab.search.matchfilter;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.ExprType;

public class ConstraintValueList extends ConstraintValue {

    private final List<Object> value;

    public ConstraintValueList(List<Object> value) {
        this.value = value;
    }

    @Override
    public boolean isTruthy() {
        return !value.isEmpty();
    }

    @Override
    public String toString() {
        return "list(" + StringUtils.join(value, ", ") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ConstraintValueList that = (ConstraintValueList) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueList ol) {
            int sizeCmp = Integer.compare(this.value.size(), ol.value.size());
            if (sizeCmp != 0)
                return sizeCmp;
            for (int i = 0; i < this.value.size(); i++) {
                Object v1 = this.value.get(i);
                Object v2 = ol.value.get(i);
                if (v1 instanceof Comparable && v2 instanceof Comparable) {
                    @SuppressWarnings("unchecked")
                    int cmp = ((Comparable<Object>) v1).compareTo(v2);
                    if (cmp != 0)
                        return cmp;
                } else {
                    int cmp = v1.toString().compareTo(v2.toString());
                    if (cmp != 0)
                        return cmp;
                }
            }
            return 0;
        }
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare list to " + other.getClass().getName());
    }

    @Override
    public List<Object> getValue() {
        return value;
    }

    @Override
    public ExprType getType() {
        return ExprType.LIST;
    }
}
