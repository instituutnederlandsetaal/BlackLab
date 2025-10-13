package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.HitGroup;

public class HitGroupPropertyIdentity extends HitGroupProperty {

    public static final String ID = "identity";

    private static final HitGroupPropertyIdentity instance = new HitGroupPropertyIdentity();

    public static HitGroupPropertyIdentity get() {
        return instance;
    }

    HitGroupPropertyIdentity(HitGroupPropertyIdentity prop, boolean invert) {
        super(prop, invert);
    }
    
    public HitGroupPropertyIdentity() {
        super();
    }
    
    @Override
    public PropertyValue get(HitGroup result) {
        return result.identity();
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        // Varies depending on what was grouped on, so we cannot say in advance.
        return PropertyValue.class;
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        if (reverse)
            return b.identity().compareTo(a.identity());
        return a.identity().compareTo(b.identity());
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public HitGroupPropertyIdentity reverse() {
        return new HitGroupPropertyIdentity(this, true);
    }

    @Override
    public String name() {
        return "group: identity";
    }
}
