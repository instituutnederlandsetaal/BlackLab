package nl.inl.blacklab.server.lib.results;

import java.util.Collection;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultSummaryCommonFields {
    private final WebserviceParams searchParam;
    private TextPattern textPattern = null;
    private final Index.IndexStatus indexStatus;
    private final SearchTimings timings;
    private final MatchInfoDefs matchInfoDefs;
    private final ResultGroups groups;
    private final WindowStats window;
    private final AnnotatedField searchField;
    private final Collection<AnnotatedField> otherFields;

    ResultSummaryCommonFields(WebserviceParams searchParam, Index.IndexStatus indexStatus,
            SearchTimings timings, MatchInfoDefs matchInfoDefs,
            ResultGroups groups, WindowStats window, AnnotatedField searchField, Collection<AnnotatedField> otherFields) {
        this.searchParam = searchParam;
        if (searchParam.hasPattern())
            this.textPattern = searchParam.pattern().orElse(null);
        this.indexStatus = indexStatus;
        this.timings = timings;
        this.matchInfoDefs = matchInfoDefs == null ? MatchInfoDefs.EMPTY : matchInfoDefs;
        this.groups = groups;
        this.window = window;
        this.searchField = searchField;
        this.otherFields = otherFields;
    }

    public WebserviceParams getSearchParam() {
        return searchParam;
    }

    public TextPattern getTextPattern() {
        return textPattern;
    }

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
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
}
