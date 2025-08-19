package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class PropertyValueContext extends PropertyValue {

    protected final Terms terms;

    protected final Annotation annotation;

    PropertyValueContext(Terms terms, Annotation annotation) {
        this.annotation = annotation;
        this.terms = terms;
    }

    public static int deserializeToken(Terms terms, String term) {
        int termId;
        if (term.equals("~"))
            termId = Constants.NO_TERM; // no token, effectively a "null" value
        else {
            if (term.startsWith("~~")) {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                term = term.substring(1);
            }
            termId = terms.indexOf(term);
        }
        return termId;
    }

    public static String serializeTerm(Terms terms, int valueTokenId) {
        String token;
        if (valueTokenId < 0)
            token = "~"; // no token, effectively a "null" value
        else {
            token = terms.get(valueTokenId);
            if (!token.isEmpty() && token.charAt(0) == '~') {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                token = "~" + token;
            }
        }
        return token;
    }

    public static String serializeTerm(String value) {
        if (value == null)
            value = "~"; // no token, effectively a "null" value
        else {
            if (!value.isEmpty() && value.charAt(0) == '~') {
                // tilde in first position has to be escaped
                // because of how null value is encoded
                value = "~" + value;
            }
        }
        return value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((annotation == null) ? 0 : annotation.hashCode());
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
        PropertyValueContext other = (PropertyValueContext) obj;
        if (annotation == null) {
            if (other.annotation != null)
                return false;
        } else if (!annotation.equals(other.annotation))
            return false;
        return true;
    }

}
