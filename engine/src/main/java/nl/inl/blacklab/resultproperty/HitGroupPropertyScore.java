package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.hitresults.HitGroup;

public class HitGroupPropertyScore extends HitGroupProperty {

    public static final String ID = "score";

    private static final HitGroupPropertyScore instance = new HitGroupPropertyScore();

    public static HitGroupPropertyScore get() {
        return instance;
    }

    HitGroupPropertyScore(HitGroupPropertyScore prop, boolean invert) {
        super(prop, invert);
    }

    public HitGroupPropertyScore() {
        super();
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueFloat.class;
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true;
    }
    
    @Override
    public PropertyValueFloat get(HitGroup result) {
        return new PropertyValueFloat(result.score());
    }

    @Override
    public int compare(HitGroup a, HitGroup b) {
        return reverse ?
                Double.compare(b.score(), a.score()) :
                Double.compare(a.score(), b.score());
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public HitGroupPropertyScore reverse() {
        return new HitGroupPropertyScore(this, true);
    }

    @Override
    public String name() {
        return "group: score";
    }
}
