package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.databind.JsonNode;

public class SolrResponse {
    private JsonNode responseHeader;

    private JsonNode response;

    private JsonNode blacklab;

    @SuppressWarnings("unused")
    private SolrResponse() {
        // no-arg ctor required by Jersey
    }

    public JsonNode getResponseHeader() {
        return responseHeader;
    }

    public JsonNode getResponse() {
        return response;
    }

    public JsonNode getBlacklab() {
        return blacklab;
    }

    @Override
    public String toString() {
        return "SolrResponse{" +
                "responseHeader=" + responseHeader +
                ", response=" + response +
                ", blacklab=" + blacklab +
                '}';
    }
}
