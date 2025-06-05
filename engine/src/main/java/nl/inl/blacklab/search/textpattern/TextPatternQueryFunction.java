package nl.inl.blacklab.search.textpattern;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.extensions.QueryExtensions;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.extensions.XFSpans;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A TextPattern that applies a function to a list of patterns to produce a new pattern.
 *
 * Right now, this is used for testing purposes, e.g. to experiment with certain optimizations,
 * specifically forward index matching.
 */
public class TextPatternQueryFunction extends TextPattern {


    private final String name;

    private final List<?> args;

    public static TextPatternQueryFunction create(String name, List<TextPattern> args) {
        return new TextPatternQueryFunction(name, args);
    }

    public TextPatternQueryFunction(String name, List<?> args) {
        if (!QueryExtensions.exists(name))
            throw new UnsupportedOperationException("Extension function not found: " + name);
        this.name = name;

        // Make sure string arguments are recognized as such (and not seen as a query)
        this.args = QueryExtensions.preprocessArgs(name, args);
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        List<Object> translated = new ArrayList<>();
        for (Object arg: args) {
            if (arg instanceof TextPattern) {
                // Translate any TextPattern arguments to BLSpanQuery
                translated.add(((TextPattern) arg).translate(context));
            } else {
                // Just copy other argument types
                translated.add(arg);
            }
        }

        return QueryExtensions.apply(name, context, translated);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((args == null) ? 0 : args.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TextPatternQueryFunction other = (TextPatternQueryFunction) obj;
        if (args == null) {
            if (other.args != null)
                return false;
        } else if (!args.equals(other.args))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "QFUNC(" + name + ", " + StringUtils.join(args, ", ") + ")";
    }

    public String getName() {
        return name;
    }

    public List<?> getArgs() {
        return args;
    }

    @Override
    public boolean isRelationsQuery() {
        if (QueryExtensions.isRelationsFunction(name))
            return true;
        for (Object arg : args) {
            if (arg instanceof TextPattern) {
                if (((TextPattern) arg).isRelationsQuery())
                    return true;
            }
        }
        return false;
    }

    /** Is this a call to with-spans()? */
    @Override
    protected boolean hasWithSpans() {
        String n = name;
        if (n.charAt(0) == '_') {
            n = n.substring(1); // remove leading underscore
        }
        return n.equals(XFSpans.FUNC_WITH_SPANS);
    }

    @Override
    protected boolean hasRspanAll() {
        // See if we're already doing explicit rspan or rel call (if so, don't add rspan(..., 'all'),
        // even if adjusthits=true)
        return getName().equals(XFRelations.FUNC_RSPAN) || getName().equals(XFRelations.FUNC_REL);
    }
}
