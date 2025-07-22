package nl.inl.blacklab.queryParser.contextql;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.WildcardQuery;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadata;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;
import nl.inl.blacklab.search.textpattern.TextPatternWildcard;
import nl.inl.util.StringUtil;

public class ContextualQueryLanguageParser {
    
    /**
     * Parse a Contextual Query Language query.
     * 
     * @param index our index
     * @param query our query
     * @return the parsed query
     * @throws InvalidQuery on parse error
     */
    public static CompleteQuery parse(BlackLabIndex index, String query) throws InvalidQuery {
        ContextualQueryLanguageParser parser = new ContextualQueryLanguageParser(index);
        return parser.parse(query);
    }

    CompleteQuery clause(BlackLabIndex index, String annotation, String relation, String term,
            String defaultAnnotation) {
        if (relation == null)
            relation = "=";
        if (annotation == null)
            annotation = defaultAnnotation;

        return switch (relation) {
            case "=" -> contains(index, annotation, term);
            case "any" -> throw new UnsupportedOperationException("any not yet supported");
            case "all" -> throw new UnsupportedOperationException("all not yet supported");
            case "exact" -> throw new UnsupportedOperationException("exact not supported");
            case "<", ">", "<=", ">=", "<>" ->
                    throw new UnsupportedOperationException("Only contains (=) relation is supported!");
            default -> throw new UnsupportedOperationException("Unknown relation operator: " + relation);
        };
    }

    CompleteQuery combineClauses(CompleteQuery a, String op, CompleteQuery b) {
        if (op.equalsIgnoreCase("and")) {
            return a.and(b);
        } else if (op.equalsIgnoreCase("or")) {
            return a.or(b);
        } else if (op.equalsIgnoreCase("not")) {
            return a.not(b);
        } else if (op.equalsIgnoreCase("prox")) {
            throw new UnsupportedOperationException("prox is not yet supported!");
        }
        throw new UnsupportedOperationException("Unrecognized operator " + op);
    }

    /**
     * Return a clause meaning "field contains value".
     *
     * Depending on the field name, this will contain a contents query or a filter
     * query.
     *
     * @param index our index
     * @param field the field name. If it starts with "contents.", it is a contents
     *            query. "contents" by itself means "contents.word". "word", "lemma"
     *            and "pos" by themselves are interpreted as being prefixed with
     *            "contents."
     * @param value the value, optionally with wildcards, to search for
     * @return the query
     */
    CompleteQuery contains(BlackLabIndex index, String field, String value) {

        boolean isContentsSearch = false;
        String annotation = index.mainAnnotatedField().mainAnnotation().name();
        boolean isProperty;
        if (index != null && !index.getClass().getSimpleName().startsWith("Mock"))
            isProperty = index.mainAnnotatedField().annotations().exists(field);
        else
            isProperty = field.equals(index.mainAnnotatedField().mainAnnotation().name()) || field.equals("lemma") || field.equals("pos"); // common case
        if (isProperty) {
            isContentsSearch = true;
            annotation = field;
        } else if (field.equals("contents")) {
            isContentsSearch = true;
        } else if (field.startsWith("contents.")) {
            isContentsSearch = true;
            annotation = field.substring(9);
        }

        String[] parts = value.trim().split(StringUtil.REGEX_WHITESPACE);
        TextPattern tp = null;
        Query q = null;
        if (parts.length == 1) {
            // Single term, possibly with wildcards
            if (isContentsSearch)
                tp = new TextPatternWildcard(value.trim(), annotation, null);
            else
                q = new WildcardQuery(new Term(field, value));
        } else {
            // Phrase query
            if (isContentsSearch) {
                List<TextPattern> clauses = new ArrayList<>();
                for (String part : parts) {
                    clauses.add(new TextPatternWildcard(part, annotation, null));
                }
                tp = new TextPatternSequence(clauses.toArray(new TextPattern[0]));
            } else {
                PhraseQuery.Builder pb = new PhraseQuery.Builder();
                for (String part : parts) {
                    pb.add(new Term(field, part));
                }
                q = pb.build();
            }
        }

        if (isContentsSearch)
            return new CompleteQuery(tp, null);
        return new CompleteQuery(null, q);
    }

    private final BlackLabIndex index;

    private String defaultProperty = "contents.word";

    public ContextualQueryLanguageParser(BlackLabIndex index) {
        this.index = index;
    }
    
    public CompleteQuery parse(String query) throws InvalidQuery {
        try {
            GeneratedContextualQueryLanguageParser parser = new GeneratedContextualQueryLanguageParser(new StringReader(query));
            parser.wrapper = this;
            return parser.query();
        } catch (ParseException | TokenMgrError e) {
            throw new InvalidQuery("Error parsing query: " + e.getMessage(), e);
        }
    }

    public void setDefaultProperty(IndexMetadata indexMetadata, String fieldName) {
        defaultProperty = fieldName + "." + indexMetadata.annotatedField(fieldName).mainAnnotation().name();
    }

    public void setDefaultProperty(Annotation annotation) {
        defaultProperty = annotation.field().name() + "." + annotation.name();
    }

    String chopEnds(String input) {
        if (input.length() >= 2)
            return input.substring(1, input.length() - 1);
        throw new IllegalArgumentException("Cannot chop ends off string shorter than 2 chars");
    }
    
    String defaultProperty() {
        return defaultProperty;
    }

    BlackLabIndex index() {
        return index;
    }

    public CompleteQuery searchClause(String annotation, String relation, String term) {
        return clause(index(), annotation, relation, term, defaultProperty());
    }

}
