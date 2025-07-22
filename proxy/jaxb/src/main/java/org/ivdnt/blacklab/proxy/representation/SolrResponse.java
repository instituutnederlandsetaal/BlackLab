package org.ivdnt.blacklab.proxy.representation;

import com.fasterxml.jackson.databind.JsonNode;

public class SolrResponse {
    private JsonNode responseHeader;

    private JsonNode response;

    private JsonNode blacklab;

    // required for Jersey
    @SuppressWarnings("unused")
    private SolrResponse() {}

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
