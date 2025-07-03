package nl.inl.blacklab.search.indexmetadata;

/**
 * An object that can be frozen.
 * 
 * A frozen object may not be modified.
 */
public interface Freezable {

    /** Can be used as a delegate to easily implement Freezable */
    class FreezeStatus implements Freezable {
        boolean frozen;

        public FreezeStatus() {
            this(false);
        }

        public FreezeStatus(boolean frozen) {
            this.frozen = frozen;
        }

        @Override
        public synchronized boolean isFrozen() {
            return frozen;
        }

        @Override
        public synchronized boolean freeze() {
            if (!frozen) {
                // apply freeze now
                frozen = true;
                return true;
            }
            // was already frozen
            return false;
        }
    }

    /**
     * Freeze if not already frozen
     * @return true if freeze was applied, false if it was already frozen
     */
    boolean freeze();
    
    boolean isFrozen();
    
    default void ensureNotFrozen() {
        if (isFrozen())
            throw new UnsupportedOperationException("Tried to modify a frozen object");
    }

    default void ensureFrozen() {
        if (!isFrozen())
            throw new UnsupportedOperationException("Tried to read a non-frozen object");
    }
}
