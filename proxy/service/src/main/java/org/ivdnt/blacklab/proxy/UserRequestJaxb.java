package org.ivdnt.blacklab.proxy;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.MultivaluedMap;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.User;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.search.UserRequest;
import nl.inl.blacklab.webservice.WebserviceOperation;

/** A user request in the JAXB [proxy] BLS web service */
public class UserRequestJaxb implements UserRequest {

    private User user;

    private final Client client;

    private final String method;

    private final String corpusName;

    private final MultivaluedMap<String, String> parameters;

    private final WebserviceOperation op;

    private final boolean isXml;

    public UserRequestJaxb(Client client, String method, String corpusName,
            MultivaluedMap<String, String> parameters, WebserviceOperation op, boolean isXml) {
        this.client = client;
        this.method = method;
        this.corpusName = corpusName;
        this.parameters = parameters;
        this.op = op;
        this.isXml = isXml;

        // TODO
        user = User.anonymous("proxy-user");
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public String getSessionId() {
        return "";
    }

    @Override
    public String getRemoteAddr() {
        return "";
    }

    @Override
    public String getHeader(String name) {
        return "";
    }

    @Override
    public String getParameter(String name) {
        return "";
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public QueryParams getParams(BlackLabIndex index, WebserviceOperation operation) {
        return null;
    }

    @Override
    public boolean isDebugMode() {
        return false;
    }

    @Override
    public String getCorpusName() {
        return "";
    }

    @Override
    public ApiVersion apiVersion() {
        return null;
    }
}
