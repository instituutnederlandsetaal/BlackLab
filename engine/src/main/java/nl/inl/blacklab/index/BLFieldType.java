package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexableFieldType;

/** A type of field to add to the Lucene index */
public interface BLFieldType {
    IndexableFieldType luceneType();
}
