package nl.inl.blacklab.resultproperty;

import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Abstract base class for criteria on which to group DocResult objects.
 * Subclasses implement specific grouping criteria (number of hits, the value of
 * a stored field in the Lucene document, ...)
 *
 * This class is thread-safe.
 * Some DocProperty instances use synchronization for threadsafety, e.g. DocPropertyStoredField,
 * because they store DocValues instances, which may only be used from one thread at a time.
 */
public abstract class DocProperty implements ResultProperty, Comparator<DocResult> {
    protected static final Logger logger = LogManager.getLogger(DocProperty.class);

    /** The segment, if these are segment-local hits */
    protected final LeafReaderContext lrc;

    /** If true, we have segment hits (lrc != null) and we should convert the
     *  segment doc/term ids to global when determining the property values. */
    boolean toGlobal;

    /** Reverse comparison result or not? */
    protected final boolean reverse;

    protected DocProperty(DocProperty prop, LeafReaderContext lrc, boolean invert) {
        this.lrc = lrc == null ? prop.lrc : lrc;
        reverse = invert ? !prop.reverse : prop.reverse;
    }

    protected DocProperty() {
        this.lrc = null;
        this.reverse = sortDescendingByDefault();
    }

    /**
     * If we have segment hits and intend to produce global property values,
     * this method will adjust the doc id to be global.
     *
     * This is different from globalDocIdForHit below: that always returns a
     * global doc id, while this method will return a segment-local doc id
     * if toGlobal is false.
     *
     * @param docId document id
     * @return (possibly) adjusted doc id
     */
    int adjustedDocId(int docId) {
        return docId + (toGlobal ? lrc.docBase : 0);
    }

    /**
     * Get a global doc id for a segment hit.
     * (because DocProperty only works with global doc ids right now)
     *
     * @param docId document id
     * @return (possibly) adjusted doc id
     */
    int globalDocIdForHit(int docId) {
        return docId + (lrc != null ? lrc.docBase : 0);
    }

    /**
     * Is the default for this property to sort descending?
     *
     * This is usually a good default for "group size" or "number of hits".
     *
     * @return whether to sort descending by default
     */
    protected boolean sortDescendingByDefault() {
        return false;
    }

    /**
     * Get the desired grouping/sorting property from the DocResult object
     *
     * @param result the result to get the grouping property for
     * @return the grouping property. e.g. this might be "Harry Mulisch" when
     *         grouping on author.
     */
    public abstract PropertyValue get(DocResult result);

    /**
     * Compares two docs on this property
     *
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        return get(a).compareTo(get(b));
    }

    @Override
    public abstract String name();

    @Override
    public abstract String serialize();

    /**
     * Used by subclasses to add a dash for reverse when serializing
     *
     * @return either a dash or the empty string
     */
    @Override
    public String serializeReverse() {
        return reverse ? "-" : "";
    }

    public static DocProperty deserialize(BlackLabIndex index, String serialized) {
        if (serialized == null || serialized.isEmpty())
            return null;

        if (PropertySerializeUtil.isMultiple(serialized))
            return deserializeMultiple(index, serialized);

        boolean reverse = false;
        if (!serialized.isEmpty() && serialized.charAt(0) == '-') {
            reverse = true;
            serialized = serialized.substring(1);
        }
        List<String> parts = PropertySerializeUtil.splitPartsList(serialized);
        String type = parts.get(0).toLowerCase();
        List<String> infos = parts.subList(1, parts.size());
        String firstInfo = infos.isEmpty() ? "" : infos.get(0);
        DocProperty result;
        switch (type) {
        case DocPropertyDecade.ID:
            result = new DocPropertyDecade(index, firstInfo);
            break;
        case DocPropertyNumberOfHits.ID:
            result = new DocPropertyNumberOfHits();
            break;
        case DocPropertyStoredField.ID:
            result = new DocPropertyStoredField(index, firstInfo);
            break;
        case DocPropertyAnnotatedFieldLength.ID:
            result = new DocPropertyAnnotatedFieldLength(index, firstInfo);
            break;

        case HitPropertyDocumentId.ID:
        case HitPropertyDoc.ID:
            throw new UnsupportedOperationException("Grouping doc results by " + type + " is not yet supported");

        case HitPropertyHitText.ID:
        case HitPropertyBeforeHit.ID:
        case HitPropertyAfterHit.ID:
        case "left":      // deprecated
        case "right":     // deprecated
        case "wordleft":  // deprecated
        case "wordright": // deprecated
        case "context":   // deprecated
        case HitPropertyHitPosition.ID:
            throw new UnsupportedOperationException("Cannot group doc results by " + type);

        default:
            logger.debug("Unknown DocProperty '" + type + "'");
            return null;
        }
        if (reverse)
            result = result.reverse();
        return result;
    }

    private static DocProperty deserializeMultiple(BlackLabIndex index, String serialized) {
        boolean reverse = false;
        if (serialized.startsWith("-(") && serialized.endsWith(")")) {
            reverse = true;
            serialized = serialized.substring(2, serialized.length() - 1);
        }
        DocProperty result = DocPropertyMultiple.deserialize(index, serialized);
        if (reverse)
            result = result.reverse();
        return result;
    }

    /**
     * Is the comparison reversed?
     *
     * @return true if it is, false if not
     */
    @Override
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Reverse the comparison.
     *
     * @return document property with the comparison reversed
     */
    @Override
    public DocProperty reverse() {
        return copyWith(null, true);
    }

    public abstract DocProperty copyWith(LeafReaderContext lrc, boolean invert);

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (reverse ? 1231 : 1237);
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
        DocProperty other = (DocProperty) obj;
        if (reverse != other.reverse)
            return false;
        return true;
    }

    /**
     * Generate a query matching the specified value for our property.
     * @param index our index (for finding field properties such as tokenized or not, analyzer, unknown value)
     * @param value value to match
     * @return query
     */
    public abstract Query query(BlackLabIndex index, PropertyValue value);

    /**
     * Can we create a query matching the specified value for our property?
     * @param index our index (for finding field properties such as tokenized or not, analyzer, unknown value)
     * @param value value to match
     * @return true if we can, false if we can't
     */
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        return false;
    }

    @Override
    public List<DocProperty> propsList() {
        return List.of(this);
    }

}
