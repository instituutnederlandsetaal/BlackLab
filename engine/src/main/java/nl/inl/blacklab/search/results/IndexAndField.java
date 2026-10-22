package nl.inl.blacklab.search.results;

import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexForQuery;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about the original query.
 */
public final class IndexAndField {

    public static IndexAndField create(BlackLabIndex index) {
        return create(index, index.mainAnnotatedField().name());
    }

    public static IndexAndField create(BlackLabIndex index, String field) {
        return new IndexAndField(index, field);
    }

    private final BlackLabIndex index;

    /** The field these hits came from (will also be used as concordance field) */
    private final String field;

    private IndexAndField(BlackLabIndex index, String field) {
        super();
        this.index = index;
        this.field = field;
    }

    /** @return the index that was searched. */
    public BlackLabIndex index() {
        return index;
    }

    public BlackLabIndexForQuery indexForQuery() {
        return index.forQuery();
    }

    /** @return field that was searched */
    public String field() {
        return field;
    }

    public AnnotatedField annotatedField() {
        return index.annotatedField(field);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IndexAndField that))
            return false;
        return Objects.equals(index, that.index) && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, field);
    }

    /**
     * Return IndexAndField with the specified field.
     *
     * Will return a new IndexAndField if the field differs from ours;
     * otherwise, will return this.
     *
     * @param field the field to set
     * @return a IndexAndField with the specified field
     */
    public IndexAndField withField(String field) {
        if (field == null)
            throw new IllegalArgumentException("field cannot be null");
        if (Objects.equals(this.field, field))
            return this;
        return IndexAndField.create(index, field);
    }
}

