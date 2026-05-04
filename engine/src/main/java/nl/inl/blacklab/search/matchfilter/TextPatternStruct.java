package nl.inl.blacklab.search.matchfilter;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import nl.inl.blacklab.search.textpattern.TextPatternDeserializer;
import nl.inl.blacklab.search.textpattern.TextPatternSerializerJson;
import nl.inl.blacklab.search.textpattern.TextPatternVisitor;

@JsonSerialize(using = TextPatternSerializerJson.class)
@JsonDeserialize(using = TextPatternDeserializer.class)
public interface TextPatternStruct {
    /**
     * Can this be represented by a bracketed expression in CorpusQL?
     *
     * It must be a single-token expression that includes annotation names.
     *
     * @return true if it is, false if it isn't
     */
    default boolean isBracketQuery() {
        return false;
    }

    int getPrecedence();

    /**
     * Visit this TextPattern, calculating some result value.
     *
     * @param visitor the visitor to call, which will handle each TextPattern subclass
     */
    <T> T accept(TextPatternVisitor<T> visitor);
}
