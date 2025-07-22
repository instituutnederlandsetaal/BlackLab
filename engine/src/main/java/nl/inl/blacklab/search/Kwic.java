package nl.inl.blacklab.search;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A "keyword in context" for a hit (left context, hit text, right context).
 *
 * This object may be converted to a {@link Concordance} object (with XML strings) by
 * calling {@link #toConcordance()}.
 *
 * Instances of this class are immutable.
 */
public class Kwic {

    private final DocFragment fragment;

    /** Token number (from start of fragment) where the hit begins. */
    private final int hitStart;

    /** Number of first token (from start of fragment) after the hit. */
    private final int hitEnd;

    /** Token number (from start of document) where this fragment begins. */
    private final int fragmentStartInDoc;

    /**
     * Construct a Kwic object
     *
     * @param annotations What annotations are stored in what order for this Kwic
     *            (e.g. word, lemma, pos)
     * @param tokens tokens (per word: the token string for each annotation)
     * @param matchStart where the match starts (token positions from start of fragment)
     * @param matchEnd where the match ends (exclusive; token positions from start of fragment)
     * @param fragmentStartInDoc where the fragment starts, in word positions
     */
    public Kwic(List<Annotation> annotations, List<String> tokens, int matchStart, int matchEnd, int fragmentStartInDoc) {
        fragment = new DocFragment(annotations, tokens);
        this.hitStart = matchStart;
        this.hitEnd = matchEnd;
        this.fragmentStartInDoc = fragmentStartInDoc;
        assert hitStart * fragment.annotations.size() <= tokens.size();
        assert hitEnd * fragment.annotations.size() <= tokens.size();
    }

    public List<String> before() {
        return Collections.unmodifiableList(fragment.tokens.subList(0, hitStart * fragment.annotations.size()));
    }

    /**
     * Get the left context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> before(Annotation annotation) {
        return singlePropertyContext(annotation, 0, hitStart);
    }

    public List<String> match() {
        int from = hitStart * fragment.annotations.size();
        int to = hitEnd * fragment.annotations.size();
        return Collections.unmodifiableList(fragment.tokens.subList(from, to));
    }

    /**
     * Get the match context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> match(Annotation annotation) {
        return singlePropertyContext(annotation, hitStart, hitEnd);
    }

    public List<String> after() {
        return Collections
                .unmodifiableList(fragment.tokens.subList(hitEnd * fragment.annotations.size(), fragment.tokens.size()));
    }

    /**
     * Get the right context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> after(Annotation annotation) {
        return singlePropertyContext(annotation, hitEnd, fragment.tokens.size() / fragment.annotations.size());
    }

    /**
     * Get all the annotations of all the tokens in the hit's context fragment.
     * 
     * @return the token annotations
     */
    public List<String> tokens() {
        return fragment.tokens();
    }

    public DocFragment fragBefore() {
        return fragment.subFragment(0, hitStart);
    }

    public DocFragment fragMatch() {
        return fragment.subFragment(hitStart, hitEnd);
    }

    public DocFragment fragAfter() {
        return fragment.subFragment(hitEnd, fragment.length());
    }

    /**
     * Get the context of a specific annotation from the complete context list.
     *
     * @param annotation the annotation to get the context for
     * @param start first word position to get the annotation context for
     * @param end word position after the last to get the annotation context for
     * @return the context for this annotation
     */
    private List<String> singlePropertyContext(Annotation annotation, int start, int end) {
        final int nProp = fragment.annotations.size();
        final int size = end - start;
        final int annotIndex = fragment.annotations.indexOf(annotation);
        final int startIndex = start * nProp + annotIndex;
        if (annotIndex == -1)
            return null;
        return new AbstractList<>() {
            @Override
            public String get(int index) {
                if (index >= size)
                    throw new IndexOutOfBoundsException();
                return fragment.tokens.get(startIndex + nProp * index);
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    /**
     * Convert this Kwic object to a Concordance object.
     *
     * This produces XML consisting of &lt;w&gt; tags. The words are the text
     * content of the tags. The punctuation is between the tags. The other
     * annotations are attributes of the tags.
     *
     * @return the Concordance object
     */
    public Concordance toConcordance() {
        return toConcordance(true);
    }

    /**
     * Convert this Kwic object to a Concordance object.
     *
     * This may either consist of only words and punctuation, or include the XML
     * tags containing the other annotations as well, depending on the parameter.
     *
     * @param produceXml if true, produces XML. If false, produces human-readable
     *            text.
     * @return the Concordance object
     */
    public Concordance toConcordance(boolean produceXml) {
        String[] conc = new String[3];
        List<String> match = match();
        String addPunctAfter = !match.isEmpty() ? match.get(0) : "";
        conc[0] = xmlString(before(), addPunctAfter, true, produceXml);
        conc[1] = xmlString(match, null, true, produceXml);
        conc[2] = xmlString(after(), null, false, produceXml);
        return new Concordance(conc);
    }

    /**
     * Convert a context List to an XML string (used for converting to a Concordance
     * object)
     *
     * @param context the context List to convert
     * @param addPunctAfter if not null, this is appended at the end of the string.
     * @param leavePunctBefore if true, no punctuation is added before the first
     *            word.
     * @param produceXml if true, produces XML with word tags. If false, produces
     *            human-readable text.
     * @return the XML string
     */
    private String xmlString(List<String> context, String addPunctAfter, boolean leavePunctBefore, boolean produceXml) {
        int valuesPerWord = fragment.annotations.size();
        int numberOfWords = context.size() / valuesPerWord;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            if (i > 0 || !leavePunctBefore) {
                if (produceXml)
                    b.append(StringEscapeUtils.escapeXml10(context.get(vIndex)));
                else
                    b.append(context.get(vIndex));
            }
            if (produceXml) {
                b.append("<w");
                for (int k = 1; k < valuesPerWord - 1; k++) {
                    String name = fragment.annotations.get(k).name();
                    String value = context.get(vIndex + 1 + j);
                    b.append(" ").append(name).append("=\"").append(StringEscapeUtils.escapeXml10(value)).append("\"");
                    j++;
                }
                b.append(">");
            } else {
                // We're skipping the other annotations besides word and punct. Advance j.
                if (valuesPerWord > 2)
                    j += valuesPerWord - 2;
            }
            if (produceXml)
                b.append(StringEscapeUtils.escapeXml10(context.get(vIndex + 1 + j))).append("</w>");
            else
                b.append(context.get(vIndex + 1 + j));
        }
        if (addPunctAfter != null)
            b.append(addPunctAfter);
        return b.toString();
    }

    /**
     * Get the annotations in the order they occur in the context array.
     * 
     * @return the annotations
     */
    public List<Annotation> annotations() {
        return fragment.annotations();
    }

    /**
     * Return the index of the token after the last hit token in the context
     * fragment.
     * 
     * @return the hit end index
     */
    public int hitEnd() {
        return hitEnd;
    }

    /**
     * Return the index of the first hit token in the context fragment.
     * 
     * @return the hit start index
     */
    public int hitStart() {
        return hitStart;
    }

    /**
     * Return the token number where this fragment starts in the document.
     *
     * @return the fragment start index
     */
    public int fragmentStartInDoc() {
        return fragmentStartInDoc;
    }

    /**
     * Return the token number where this fragment ends in the document.
     *
     * @return the fragment end index (first word not in fragment)
     */
    public int fragmentEndInDoc() {
        return fragmentStartInDoc + fragment.length();
    }

    public DocFragment fragment() {
        return fragment;
    }

    @Override
    public String toString() {
        return toConcordance().toString();
    }
}
