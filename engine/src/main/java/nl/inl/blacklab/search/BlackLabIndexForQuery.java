package nl.inl.blacklab.search;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;

/** Partial interface to a BlackLabIndex needed by BLSpanQuery implementations. */
public interface BlackLabIndexForQuery {

    /** If a parallel version is passed without the base name of the annotated field,
     *  return the full annotated field name.
     *
     *  E.g. when passing "nl", return "contents__nl", assuming "contents" is the
     *  base name of the main annotated field in a parallel corpus.
     */
    String fullAnnotatedFieldName(String fieldOrVersion);

    boolean hasForwardIndex(String annotatedField, String annotation);

    ForwardIndexAccessor forwardIndexAccessor(String annotatedFieldName);

    BlackLabEngine blackLab();
}
