package nl.inl.blacklab.codec;

class RelationInfoFieldMutable extends RelationInfoField {
    public RelationInfoFieldMutable(String fieldName) {
        super(fieldName);
    }

    public void setDocsOffset(long offset) {
        this.docsOffset = offset;
    }
}
