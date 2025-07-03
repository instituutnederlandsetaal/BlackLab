package nl.inl.blacklab.exceptions;

public class MatchInfoNotFound extends InvalidQuery {

    private final String name;

    public MatchInfoNotFound(String name) {
        super("Reference to unknown match info (e.g. capture group): " + name);
        this.name = name;
    }

    public String getMatchInfoName() {
        return name;
    }
}
