package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.PostingsFormat;

public abstract class BlackLabPostingsFormat extends PostingsFormat {
    /** Every file extension will be prefixed with this to indicate it is part of the forward index. */
    private static final String EXT_PREFIX = "blfi.";
    /** Extension for the fields file. This stores the annotated field name and the offset
     in the term index file where the term offsets ares stored.*/
    public static final String FIELDS_EXT = EXT_PREFIX + "fields";
    /** Extension for the term index file, that stores the offset in the terms file where
     the term strings start for each term (in each annotated field). */
    public static final String TERMINDEX_EXT = EXT_PREFIX + "termindex";
    /** Extension for the terms file, where the term strings are stored. */
    public static final String TERMS_EXT = EXT_PREFIX + "terms";
    /** Extension for the terms order file, where indices for different sorts of the term strings are stored.
     * pos2IDInsensitive, id2PosInsensitive, pos2IDSensitive, id2PosSensitive */
    public static final String TERMORDER_EXT = EXT_PREFIX + "termorder";
    /** Extension for the tokens index file, that stores the offsets in the tokens file
     where the tokens for each document are stored. */
    public static final String TOKENS_INDEX_EXT = EXT_PREFIX + "tokensindex";
    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    public static final String TOKENS_EXT = EXT_PREFIX + "tokens";
    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    public static final String TERMVEC_TMP_EXT = EXT_PREFIX + "termvec.tmp";

    /** Every relation info file extension will be prefixed with this to indicate it is part of the relation info. */
    private static final String EXT_RELINFO_PREFIX = "blri.";

    /** Relation info fields (gives offset into docs file for each field) */
    static final String RI_FIELDS_EXT = EXT_RELINFO_PREFIX + "fields";

    /** Relation info docs (gives offset into relations file for each doc) */
    static final String RI_DOCS_EXT = EXT_RELINFO_PREFIX + "docs";

    /** Relation info docs (gives offset into attrsets file for each relation) */
    static final String RI_RELATIONS_EXT = EXT_RELINFO_PREFIX + "relations";

    /** Relation info docs (gives attribute name index and offset into attrvalues file for each attribute to a relation) */
    static final String RI_ATTR_SETS_EXT = EXT_RELINFO_PREFIX + "attrsets";

    /** Relation info attribute names */
    static final String RI_ATTR_NAMES_EXT = EXT_RELINFO_PREFIX + "attrnames";

    /** Relation info attribute values */
    static final String RI_ATTR_VALUES_EXT = EXT_RELINFO_PREFIX + "attrvalues";

    /** Extension for the temporary relations file that will be converted later.
     * The temporary file contains the attribute set id for each unique relation+attributes in each doc (and each annotated field)
     */
    static final String RI_RELATIONS_TMP_EXT = EXT_RELINFO_PREFIX + "relations.tmp";

    public BlackLabPostingsFormat(String name) {
        super(name);
    }
}
