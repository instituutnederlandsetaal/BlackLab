package nl.inl.blacklab.server.lib.results;

import java.util.Collection;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryCommonFields {
    private final ParamsForResponse paramsForResponse;
    private final TextPattern textPattern;
    private final SearchTimings timings;
    private final MatchInfoDefs matchInfoDefs;
    private final ResultGroups groups;
    private final WindowStats window;
    private final AnnotatedField searchField;
    private final Collection<AnnotatedField> otherFields;
    private final SampleParameters sampleSettings;
    private final ResultSummaryNumDocs numDocs;
    private final ResultSummaryNumHits numHits;

    public ResultSummaryCommonFields(TextPattern pattern, SearchTimings timings, MatchInfoDefs matchInfoDefs, ResultGroups groups,
            WindowStats window, AnnotatedField searchField, Collection<AnnotatedField> otherFields,
            SampleParameters sampleSettings, ParamsForResponse paramsForResponse,
            ResultSummaryNumDocs numDocs, ResultSummaryNumHits numHits) {
        this.paramsForResponse = paramsForResponse;
        this.textPattern = pattern;
        this.timings = timings;
        this.matchInfoDefs = matchInfoDefs == null ? MatchInfoDefs.EMPTY : matchInfoDefs;
        this.groups = groups;
        this.window = window;
        this.searchField = searchField;
        this.otherFields = otherFields;
        this.sampleSettings = sampleSettings;
        this.numDocs = numDocs;
        this.numHits = numHits;
    }

    public ParamsForResponse getParamsForResponse() {
        return paramsForResponse;
    }

    public TextPattern getTextPattern() {
        return textPattern;
    }

    public SearchTimings getTimings() {
        return timings;
    }

    public MatchInfoDefs getMatchInfoDefs() {
        return matchInfoDefs;
    }

    public ResultGroups getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }

    public AnnotatedField getSearchField() {
        return searchField;
    }

    public Collection<AnnotatedField> getOtherFields() {
        return otherFields;
    }

    public SampleParameters sampleParams() {
        return sampleSettings;
    }

    public ResultSummaryNumDocs getNumDocs() {
        return numDocs;
    }

    public ResultSummaryNumHits getNumHits() {
        return numHits;
    }
}
