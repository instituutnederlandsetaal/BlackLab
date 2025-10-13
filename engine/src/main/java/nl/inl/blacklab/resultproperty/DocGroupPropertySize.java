package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.DocGroup;

public class DocGroupPropertySize extends DocGroupProperty {

    public static final String ID = HitGroupPropertySize.ID;

    private static final DocGroupPropertySize instance = new DocGroupPropertySize();

    public static DocGroupPropertySize get() {
        return instance;
    }

    DocGroupPropertySize(DocGroupPropertySize prop, boolean invert) {
        super(prop, invert);
    }
    
    public DocGroupPropertySize() {
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
    public PropertyValueInt get(DocGroup result) {
        return new PropertyValueInt(result.size());
    }

    @Override
    public int compare(DocGroup a, DocGroup b) {
        return reverse ?
                Long.compare(b.size(), a.size()) :
                Long.compare(a.size(), b.size());
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public DocGroupPropertySize reverse() {
        return new DocGroupPropertySize(this, true);
    }

    @Override
    public String name() {
        return "group: size";
    }
}
