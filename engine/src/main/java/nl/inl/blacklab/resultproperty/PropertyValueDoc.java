package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.util.PropertySerializeUtil;

/** Property value that represents a BlackLab document */
public class PropertyValueDoc extends PropertyValue {

    private final int docId;

    @Override
    public Integer value() {
        return docId;
    }

    public PropertyValueDoc(int id) {
        this.docId = id;
    }

    @Override
    public int compareTo(Object o) {
        return Integer.compare(docId, ((PropertyValueDoc) o).docId);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(docId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (obj instanceof PropertyValueDoc) {
            return docId == ((PropertyValueDoc) obj).docId;
        }
        return false;
    }

    public static PropertyValue deserialize(String strDocId) {
        int id;
        try {
            id = Integer.parseInt(strDocId);
        } catch (NumberFormatException e) {
            logger.warn("PropertyValueDoc.deserialize(): '" + strDocId + "' is not a valid integer.");
            id = -1;
        }
        return new PropertyValueDoc(id);
    }

    @Override
    public String toString() {
        return Integer.toString(docId);
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("doc", Integer.toString(docId));
    }
}
