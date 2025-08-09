package nl.inl.blacklab.codec;

class ForwardIndexFieldMutable extends ForwardIndexField {
    public ForwardIndexFieldMutable(String fieldName) {
        super(fieldName);
    }

    public void setNumberOfTerms(int number) {
        this.numberOfTerms = number;
    }

    public void setTermIndexOffset(long offset) {
        this.termIndexOffset = offset;
    }

    public void setTermOrderOffset(long offset) {
        this.termOrderOffset = offset;
    }

    public void setTokensIndexOffset(long offset) {
        this.tokensIndexOffset = offset;
    }
}
