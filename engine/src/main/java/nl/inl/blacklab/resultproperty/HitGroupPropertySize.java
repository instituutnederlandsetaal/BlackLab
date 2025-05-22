package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.HitGroup;

public class HitGroupPropertySize extends HitGroupProperty {

    public static final String ID = "size";

    HitGroupPropertySize(HitGroupPropertySize prop, boolean invert) {
        super(prop, invert);
    }
    
    public HitGroupPropertySize() {
        super();
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true;
    }
    
    @Override
    public PropertyValueInt get(HitGroup result) {
        return new PropertyValueInt(result.size());
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        int cmpResult = Long.compare(a.size(), b.size());
        return reverse ? -cmpResult : cmpResult;
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public HitGroupPropertySize reverse() {
        return new HitGroupPropertySize(this, true);
    }

    @Override
    public String name() {
        return "group: size";
    }
}
