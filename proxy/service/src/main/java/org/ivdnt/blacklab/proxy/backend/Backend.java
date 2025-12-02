package org.ivdnt.blacklab.proxy.backend;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestFieldInfo;
import nl.inl.blacklab.server.lib.results.ApiVersion;

/** Represents a backend that executes requests (e.g. proxy to another BLS, or directly to BlackLab) */
public interface Backend {

    default void close() {
        // NOP
    }

    Response corpusInfo(ApiVersion apiVersion, RequestCorpusInfo req);

    Response field(ApiVersion apiVersion, RequestFieldInfo req);

    Response hits(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method);

    Response docs(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method);

    Response parsePattern(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method);

    Response relations(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method);

    Response docInfo(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method);

    Response docContents(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method);

    Response docSnippet(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method);

    Response termFreq(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters, String method);

    Response status(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method);

    Response autocompleteMetadata(ApiVersion apiVersion, String corpusName, String fieldName,
            MultivaluedMap<String, String> parameters,
            String method);

    Response autocompleteAnnotated(ApiVersion apiVersion, String corpusName, String fieldName, String annotationName,
            MultivaluedMap<String, String> parameters, String method);

    Response serverInfo(ApiVersion apiVersion, String method);

    Response listInputFormats(ApiVersion apiVersion, MultivaluedMap<String, String> parameters, String method);

    Response inputFormat(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method);

    Response inputFormatXslt(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method);
}
