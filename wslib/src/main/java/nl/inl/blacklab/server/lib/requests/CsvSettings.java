package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

/** A request to filter hits by a property and value */
public record CsvSettings(boolean declareSeparator, boolean includeSummary) {
    public static CsvSettings fromParams(QueryParams qpar) {
        return new CsvSettings(
                qpar.getBool(WsParam.CSV_DECLARE_SEPARATOR),
                qpar.getBool(WsParam.CSV_INCLUDE_SUMMARY)
        );
    }
}
