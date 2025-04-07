package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.DocGroup;

public class DocGroupPropertyIdentity extends DocGroupProperty {

    public static final String ID = HitGroupPropertyIdentity.ID;

    DocGroupPropertyIdentity(DocGroupPropertyIdentity prop, boolean invert) {
        super(prop, invert);
    }
    
    public DocGroupPropertyIdentity() {
        super();
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        // Varies depending on what was grouped on, so we cannot say in advance.
        return PropertyValue.class;
    }

    @Override
    public PropertyValue get(DocGroup result) {
        return result.identity();
    }

    @Override
    public int compare(DocGroup a, DocGroup b) {
        if (reverse)
            return b.identity().compareTo(a.identity());
        return a.identity().compareTo(b.identity());
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public DocGroupPropertyIdentity reverse() {
        return new DocGroupPropertyIdentity(this, true);
    }

    @Override
    public String name() {
        return "group: identity";
    }
}
