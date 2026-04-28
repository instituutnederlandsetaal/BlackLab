package nl.inl.blacklab.plugins.param;

import nl.inl.blacklab.search.lucene.MatchInfo;

public record PMatchInfo(
    String name,
    boolean isRequired
) implements PluginParam {

    public static PMatchInfo optional(String name) {
        return new PMatchInfo(name, false);
    }

    public static PMatchInfo required(String name) {
        return new PMatchInfo(name, true);
    }

    @Override
    public MatchInfo validate(Object raw) {
        if (raw instanceof MatchInfo matchInfo) {
            return matchInfo;
        }
        throw new InvalidPluginParameters(msgNamePrefix() + "not a match info: " + raw);
    }
}
