package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.server.lib.QueryParams;

/** A request to filter hits by a property and value */
public record CsvSettings(boolean declareSeparator, boolean includeSummary) {
    public static CsvSettings fromParams(QueryParams qpar) {
        return new CsvSettings(
            qpar.getCsvDeclareSeparator(),
            qpar.getCsvIncludeSummary()
        );
    }
}
