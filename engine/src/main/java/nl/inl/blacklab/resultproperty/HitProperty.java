package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.icu.text.CollationKey;

import it.unimi.dsi.fastutil.longs.LongComparator;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * Abstract base class for a property of a hit, like document title, hit text,
 * right context, etc.
 */
public abstract class HitProperty implements ResultProperty, LongComparator {
    protected static final Logger logger = LogManager.getLogger(HitProperty.class);

    protected final PropContext context;

    public static HitProperty deserialize(Hits hits, String serialized, ContextSize contextSize) {
        return deserialize(hits.index(), hits.field(), serialized, contextSize);
    }

    /**
     * Convert the String representation of a HitProperty back into the HitProperty
     *
     * @param index our index
     * @param field field we're searching
     * @param serialized the serialized object
     * @return the HitProperty object, or null if it could not be deserialized
     */
    public static HitProperty deserialize(BlackLabIndex index, AnnotatedField field, String serialized, ContextSize contextSize) {
        if (serialized == null || serialized.isEmpty())
            return null;
        contextSize = ensureNumeric(index, contextSize);

        if (PropertySerializeUtil.isMultiple(serialized))
            return deserializeMultiple(index, field, serialized, contextSize);

        List<String> parts = PropertySerializeUtil.splitPartsList(serialized);
        String type = parts.get(0).toLowerCase();
        boolean reverse = false;
        if (!type.isEmpty() && type.charAt(0) == '-') {
            reverse = true;
            type = type.substring(1);
        }
        List<String> infos = parts.subList(1, parts.size());
        HitProperty result;
        switch (type) {
        case HitPropertyAlignments.ID:
            result = HitPropertyAlignments.deserializeProp(index, field, infos);
            break;
        case HitPropertyDocumentDecade.ID:
            if (infos.isEmpty())
                throw new IllegalArgumentException("No decade specified for " + HitPropertyDocumentDecade.ID);
            result = HitPropertyDocumentDecade.deserializeProp(index, infos.get(0));
            break;
        case HitPropertyDoc.ID:
            result = new HitPropertyDoc(index);
            break;
        case HitPropertyDocumentId.ID:
            result = new HitPropertyDocumentId();
            break;
        case HitPropertyDocumentStoredField.ID:
            if (infos.isEmpty())
                throw new IllegalArgumentException("No field specified for " + HitPropertyDocumentStoredField.ID);
            result = new HitPropertyDocumentStoredField(index, infos.get(0));
            break;
        case HitPropertyHitText.ID:
            result = HitPropertyHitText.deserializeProp(index, field, infos);
            break;
        case "left": // deprecated
        case HitPropertyBeforeHit.ID:
            result = HitPropertyBeforeHit.deserializeProp(index, field, infos, contextSize);
            break;
        case "right": // deprecated
        case HitPropertyAfterHit.ID:
            result = HitPropertyAfterHit.deserializeProp(index, field, infos, contextSize);
            break;
        case "wordleft":
            // deprecated, use e.g. before:lemma:s:1
            result = HitPropertyBeforeHit.deserializePropSingleWord(index, field, infos);
            break;
        case "wordright":
            // deprecated, use e.g. after:lemma:s:1
            result = HitPropertyAfterHit.deserializePropSingleWord(index, field, infos);
            break;
        case HitPropertyContextPart.ID:
            result = HitPropertyContextPart.deserializeProp(index, field, infos);
            break;
        case "context":
            // deprecated, will be serialized to (multiple) ctx
            result = HitPropertyContextPart.deserializePropContextWords(index, field, infos);
            break;
        case HitPropertyCaptureGroup.ID:
            result = HitPropertyCaptureGroup.deserializeProp(index, field, infos);
            break;
        case HitPropertySpanAttribute.ID:
            result = HitPropertySpanAttribute.deserializeProp(index, field, infos);
            break;
        case HitPropertyHitPosition.ID:
            result = new HitPropertyHitPosition();
            break;

        case DocPropertyAnnotatedFieldLength.ID:
            throw new UnsupportedOperationException("Grouping hit results by " + type + " is not yet supported");

        case DocPropertyNumberOfHits.ID:
            throw new InvalidQuery("Cannot group hit results by " + type);

        default:
            logger.debug("Unknown HitProperty '" + type + "'");
            return null;
        }
        if (reverse)
            result = result.reverse();
        return result;
    }

    static HitProperty deserializeMultiple(BlackLabIndex index, AnnotatedField field, String serialized,
            ContextSize contextSize) {
        boolean reverse = false;
        if (serialized.startsWith("-(") && serialized.endsWith(")")) {
            reverse = true;
            serialized = serialized.substring(2, serialized.length() - 1);
        }
        HitProperty result = HitPropertyMultiple.deserializeProp(index, field, serialized,
                contextSize);
        if (reverse)
            result = result.reverse();
        return result;
    }

    /**
     * Make sure we have a numeric context size for determining default context property size.
     *
     * If the specified context size is null, or based on an inline tag,
     * we'll use the default context size for the index.
     *
     * @param index the index
     * @param contextSize the context size to check
     * @return the numeric context size
     */
    private static ContextSize ensureNumeric(BlackLabIndex index, ContextSize contextSize) {
        if (contextSize == null || contextSize.isInlineTag()) {
            // No context size specified, or context depends on inline tag like <s/>; just use the default context
            // size to assign any default hitproperty context sizes.
            contextSize = index.defaultContextSize();
        }
        return contextSize;
    }

    /** Reverse comparison result or not? */
    protected boolean reverse;

    protected HitProperty() {
        this.context = PropContext.NO_CHANGE;
        this.reverse = sortDescendingByDefault();
    }

    /**
     * Copy a HitProperty, with some optional changes.
     *
     * @param prop property to copy
     * @param context requested changes in context, if any
     * @param invert true to invert the previous sort order; false to keep it the same
     */
    HitProperty(HitProperty prop, PropContext context, boolean invert) {
        this.context = prop.context.adjustedWith(context);
        this.reverse = invert ? !prop.reverse : prop.reverse;
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

    public abstract PropertyValue get(long hitIndex);

    public String getString(long hitIndex) {
        return get(hitIndex).value().toString();
    }

    public CollationKey getCollationKey(long hitIndex) {
        Map<String, CollationKey> cache = context.collationCache();
        String str = getString(hitIndex);
        return cache == null ? PropertyValue.collator.getCollationKey(str) :
                cache.computeIfAbsent(str, PropertyValue.collator::getCollationKey);
    }

    // A default implementation is nice, but slow.
    @Override
    public int compare(long indexA, long indexB) {
        PropertyValue hitPropValueA = get(indexA);
        PropertyValue hitPropValueB = get(indexB);
        return reverse ?
                hitPropValueB.compareTo(hitPropValueA) :
                hitPropValueA.compareTo(hitPropValueB);
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

    @Override
    public HitProperty reverse() {
        return copyWith(PropContext.NO_CHANGE, true);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits object.
     *
     * @param hits new Hits to use
     * @return the new HitProperty object
     */
    public HitProperty copyWith(Hits hits) {
        if (context.hits() == hits)
            return this;
        return copyWith(PropContext.globalHits(hits), false);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits and Contexts
     * object.
     *
     * @param context           property context (hits, segment, etc.)
     * @return the new HitProperty object
     */
    public HitProperty copyWith(PropContext context) {
        if (this.context.equals(context))
            return this;
        return copyWith(context, false);
    }

    /**
     * Produce a copy of this HitProperty object with a different Hits and Contexts
     * object.
     *
     * @param context           property context (hits, segment, etc.)
     * @param invert            true if we should invert the previous sort order; false to keep it the same
     * @return the new HitProperty object
     */
    public abstract HitProperty copyWith(PropContext context, boolean invert);

    @Override
    public boolean isReverse() {
        return reverse;
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HitProperty that = (HitProperty) o;
        return reverse == that.reverse;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reverse);
    }

    private List<HitProperty> props() {
        return null;
    }

    @Override
    public List<HitProperty> propsList() {
        return isCompound() ? props() : List.of(this);
    }

    /**
     * Return only the DocProperty portion (if any) of this HitProperty, if any.
     *
     * E.g. if this is a HitPropertyMultiple of HitPropertyContextWords and HitPropertyDocumentStoredField,
     * return the latter as a DocPropertyStoredField.
     *
     * This is used for calculating the relative frequency when grouping on a metadata field.
     *
     * It is also used in HitGroupsTokenFrequencies to speed up large frequency list requests.
     *
     * @return metadata portion of this property, or null if there is none
     */
    public DocProperty docPropsOnly() {
        return null;
    }

    /**
     * Return only the values corresponding to DocProperty's of the given PropertyValue, if any.
     *
     * E.g. if this is a HitPropertyMultiple of HitPropertyContextWords and HitPropertyDocumentStoredField,
     * return the latter of the two values in the supplied PropertyValue.
     *
     * This is used for calculating the relative frequency when grouping on a metadata field.
     *
     * @param value value to extract the values corresponding to DocProperty's from
     * @return metadata portion of this value, or null if there is none
     */
    public PropertyValue docPropValues(PropertyValue value) {
        return null;
    }

    /**
     * Does this property only use the hit's direct annotations (word, lemma, etc... not surrounding context) and/or properties of the hit's document (metadata).
     * For example, as derived statistic (such as group size, document length, decade) should return FALSE here.
     * Properties that just read docValues and such should return TRUE.
     * @return true if it does, false if not
     */
    public abstract boolean isDocPropOrHitText();

    public PropContext getContext() {
        return context;
    }
}
