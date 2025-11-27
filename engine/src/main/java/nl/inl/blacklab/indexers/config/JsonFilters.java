package nl.inl.blacklab.indexers.config;

import nl.inl.blacklab.search.indexmetadata.FieldType;

/** Custom filters for JSON serialization */
public class JsonFilters {
    // used for some booleans
    static class IsTrue {
        public boolean equals(Object obj) {
            return obj != null && (boolean) obj;
        }
    }

    // user for field type
    static class IsTokenized {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof FieldType) {
                return obj == FieldType.TOKENIZED;
            }
            return false;
        }
    }

    // used for e.g. analyzer
    static class IsDefault {
        @Override
        public boolean equals(Object obj) {
            return obj.toString().equalsIgnoreCase("default");
        }
    }

    // used for unknownValue
    static class IsUnknown {
        @Override
        public boolean equals(Object obj) {
            return obj.toString().equalsIgnoreCase("unknown");
        }
    }
}
