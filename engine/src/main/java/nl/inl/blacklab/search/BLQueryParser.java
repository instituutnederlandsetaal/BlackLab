package nl.inl.blacklab.search;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.textpattern.CompleteQuery;

/** Parses textual queries in some query language. */
public interface BLQueryParser {

    /** Parse a query to a text pattern and/or metadata query */
    CompleteQuery parse(String query) throws InvalidQuery;

}
