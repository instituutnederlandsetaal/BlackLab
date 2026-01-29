package nl.inl.blacklab.search.matchfilter;

import java.util.Objects;

import nl.inl.blacklab.plugins.ExprType;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class ConstraintValueMatchInfo extends ConstraintValue {

    final MatchInfo matchInfo;

    public ConstraintValueMatchInfo(MatchInfo matchInfo) {
        this.matchInfo = matchInfo;
    }

    public MatchInfo getValue() {
        return matchInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConstraintValueMatchInfo that))
            return false;
        return Objects.equals(matchInfo, that.matchInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(matchInfo);
    }

    @Override
    public int compareTo(ConstraintValue other) {
        if (other instanceof ConstraintValueMatchInfo) {
            return matchInfo.compareTo(((ConstraintValueMatchInfo) other).matchInfo);
        }
        throw new IllegalArgumentException("Can only compare equal types! Tried to compare matchInfo to " + other.getClass().getName());
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public String toString() {
        return matchInfo.toString();
    }

    @Override
    public ExprType getType() {
        return ExprType.MATCH_INFO;
    }

}
