package org.ivdnt.blacklab.solr;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.DocSet;

import com.fasterxml.jackson.core.JsonProcessingException;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

public class UserRequestSolr implements UserRequest {

    /** BlackLab parameters are prefixed with this in Solr requests */
    public static final String BL_PAR_PREFIX = "bl.";

    private final ResponseBuilder rb;

    private final BlackLabSearchComponent searchComponent;

    private User user;

    public UserRequestSolr(ResponseBuilder rb, BlackLabSearchComponent searchComponent) {
        this.rb = rb;
        this.searchComponent = searchComponent;
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
    public String getSessionId() {
        return null;
    }

    @Override
    public String getRemoteAddr() {
        if (rb.req.getHttpSolrCall() == null)
            return "UNKNOWN"; // test
        final HttpServletRequest req = rb.req.getHttpSolrCall().getReq();
        String header = req.getHeader("X-Forwarded-For");
        if (header != null && !header.isEmpty()) header = req.getRemoteAddr();
        return header;
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
    public QueryParams getParams(BlackLabIndex index, WebserviceOperation operation) {
        SolrParams solrParams = rb.req.getParams();
        String blReq = solrParams.get(BL_PAR_PREFIX + WsParam.JSON_REQUEST);

        // If no explicit bl.filter specified; use Solr's document results as our filter query
        DocSetFilter fallbackFilterQuery = null;
        DocSet docSet = rb.getResults() != null ? rb.getResults().docSet : null;
        if (docSet != null && docSet.size() > 0 && index != null) {
            fallbackFilterQuery = new DocSetFilter(docSet, index.metadata().metadataDocId());
        }

        BLSConfig config = config();
        QueryParams qpSolr;
        boolean isDebugMode = isDebugMode();
        if (blReq != null) {
            // Request was passed as a JSON structure. Parse that.
            try {
                qpSolr = QueryParams.fromJson(getCorpusName(), operation, blReq, fallbackFilterQuery, config, isDebugMode);
            } catch (JsonProcessingException e) {
                throw new BadRequest("INVALID_JSON", "Error parsing bl.req parameter", e);
            }
        } else {
            // Request was passed as separate bl.* parameters. Parse them.
            qpSolr = QueryParamsSolrUtil.getParams(getCorpusName(), solrParams, fallbackFilterQuery, config, isDebugMode);
        }
        return qpSolr;
    }

    @Override
    public String getCorpusName() {
        return rb.req.getCore().getName();
    }

    @Override
    public boolean isDebugMode() {
        return rb.req.getHttpSolrCall() == null || getSearchManager().isDebugMode(getRemoteAddr());
    }

    @Override
    public ApiVersion apiVersion() {
        String paramApi = rb.req.getParams().get(BL_PAR_PREFIX + WsParam.API);
        return paramApi == null ? config().getParameters().getApi() :
                ApiVersion.fromValue(paramApi);
    }
}
