package org.ivdnt.blacklab.solr;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.DocSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.instrumentation.RequestInstrumentationProvider;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.QueryParamsJson;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.webservice.WebserviceOperation;

public class UserRequestSolr implements UserRequest {

    private final ResponseBuilder rb;

    private final BlackLabSearchComponent searchComponent;

    private final SearchManager searchMan;

    private User user;

    public UserRequestSolr(ResponseBuilder rb, BlackLabSearchComponent searchComponent) {
        this.rb = rb;
        this.searchComponent = searchComponent;
        this.searchMan = searchComponent.getSearchManager();
    }

    @Override
    public synchronized User getUser() {
        if (user == null) {
            //AuthMethod authObj = getSearchManager().getAuthSystem().getAuthObject();
            // TODO: detect logged-in user vs. anonymous user with session id
            Principal p = rb.req.getUserPrincipal();
            user = User.anonymous(p == null ? "UNKNOWN" : p.getName());
        }
        return user;
    }

    @Override
    public SearchManager getSearchManager() {
        return searchMan;
    }

    @Override
    public String getSessionId() {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        if (rb.req.getHttpSolrCall() == null)
            return "UNKNOWN"; // test
        final HttpServletRequest req = rb.req.getHttpSolrCall().getReq();
        String header = req.getHeader("X-Forwarded-For");
        if (header != null && header.length() > 0) header = req.getRemoteAddr();
        return header;
    }

    @Override
    public String getPersistedUserId() {
        return null;
    }

    @Override
    public void persistUser(User user, int durationSec) {

    }

    @Override
    public String getHeader(String name) {
        return null;
    }

    @Override
    public String getParameter(String name) {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public WebserviceParams getParams(BlackLabIndex index, WebserviceOperation operation) {
        User user = getUser();
        SolrParams solrParams = rb.req.getParams();
        String blReq = solrParams.get("bl.req");
        QueryParams qpSolr;
        if (blReq != null) {
            // Request was passed as a JSON structure. Parse that.
            try {
                qpSolr = new QueryParamsJson(getCorpusName(), searchMan, user, blReq, operation);
            } catch (JsonProcessingException e) {
                throw new BadRequest("INVALID_JSON", "Error parsing bl.req parameter", e);
            }
        } else {
            // Request was passed as separate bl.* parameters. Parse them.
            qpSolr = new QueryParamsSolr(getCorpusName(), searchMan, solrParams, user);
        }
        try {
            operation = qpSolr.getOperation();
        } catch (UnsupportedOperationException e) {
            throw new BadRequest("UNKNOWN_OPERATION", "Unknown operation");
        }
        boolean isDocs = operation.isDocsOperation();
        WebserviceParamsImpl params = WebserviceParamsImpl.get(isDocs, isDebugMode(), qpSolr);
        if (params.getDocumentFilterQuery().isEmpty()) {
            // No explicit bl.filter specified; use Solr's document results as our filter query
            DocSet docSet = rb.getResults() != null ? rb.getResults().docSet : null;
            if (docSet != null && docSet.size() > 0 && index != null)
                params.setFilterQuery(new DocSetFilter(docSet, index.metadata().metadataDocId()));
        }
        return params;
    }

    @Override
    public String getCorpusName() {
        return rb.req.getCore().getName();
    }

    @Override
    public boolean isDebugMode() {
        return rb.req.getHttpSolrCall() == null || searchMan.isDebugMode(getRemoteAddr());
    }

    @Override
    public RequestInstrumentationProvider getInstrumentationProvider() {
        return searchComponent.getInstrumentationProvider();
    }

    @Override
    public ApiVersion apiVersion() {
        String paramApi = rb.req.getParams().get("bl.api");
        return paramApi == null ? searchMan.config().getParameters().getApi() :
                ApiVersion.fromValue(paramApi);
    }
}
