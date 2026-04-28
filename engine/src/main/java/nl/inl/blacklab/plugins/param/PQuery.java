package nl.inl.blacklab.plugins.param;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public record PQuery(
    String name,
    boolean isRequired
) implements PluginParam {

    public static PQuery optional(String name) {
        return new PQuery(name, false);
    }

    public static PQuery required(String name) {
        return new PQuery(name, true);
    }

    @Override
    public BLSpanQuery validate(Object raw) {
        if (raw instanceof BLSpanQuery query) {
            return query;
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "not a query: " + raw);
    }
}
