package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.server.lib.User;

public record RequestCorpusInfo(String corpusName, User user, Collection<String> listValuesFor, long limitValues,
                                boolean customInfo) {
}
