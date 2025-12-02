package org.ivdnt.blacklab.proxy.backend.blacklab;

import org.ivdnt.blacklab.proxy.backend.Backend;
import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.AutocompleteResponse;
import org.ivdnt.blacklab.proxy.representation.Corpus;
import org.ivdnt.blacklab.proxy.representation.CorpusStatus;
import org.ivdnt.blacklab.proxy.representation.DocContentsResults;
import org.ivdnt.blacklab.proxy.representation.DocInfoResponse;
import org.ivdnt.blacklab.proxy.representation.DocSnippetResponse;
import org.ivdnt.blacklab.proxy.representation.DocsResults;
import org.ivdnt.blacklab.proxy.representation.HitsResults;
import org.ivdnt.blacklab.proxy.representation.InputFormatInfo;
import org.ivdnt.blacklab.proxy.representation.InputFormatXsltResults;
import org.ivdnt.blacklab.proxy.representation.InputFormats;
import org.ivdnt.blacklab.proxy.representation.JsonCsvResponse;
import org.ivdnt.blacklab.proxy.representation.ParsePatternResponse;
import org.ivdnt.blacklab.proxy.representation.RelationsResponse;
import org.ivdnt.blacklab.proxy.representation.Server;
import org.ivdnt.blacklab.proxy.representation.TermFreqList;
import org.ivdnt.blacklab.proxy.representation.TokenFreqList;
import org.ivdnt.blacklab.proxy.resources.ParamsUtil;
import org.ivdnt.blacklab.proxy.resources.SimpleResponse;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestFieldInfo;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;

/** A backend that uses BlackLab directly (no proxy) */
public class BlacklabBackend implements Backend {

    private final ResponseConverter converterLegacy;

    private final ResponseConverter converterNew;

    public BlacklabBackend() {
        this.converterLegacy = new ResponseConverter(false);
        this.converterNew = new ResponseConverter(true);
    }

    ResponseConverter conv(ApiVersion apiVersion) {
        return apiVersion.getMajor() >= 5 ? converterNew : converterLegacy;
    }

    @Override
    public Response corpusInfo(ApiVersion apiVersion, RequestCorpusInfo req) {
        // TODO: full API version handling
        Corpus corpus = conv(apiVersion).corpus(req, WebserviceOperations.corpusInfo(req));
        return SimpleResponse.success(corpus);
    }

    @Override
    public Response field(ApiVersion apiVersion, RequestFieldInfo req) {
        return SimpleResponse.success(conv(apiVersion).field(req, WebserviceOperations.field(req)));
    }

    @Override
    public Response hits(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response docs(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response parsePattern(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response relations(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response docInfo(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response docContents(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response docSnippet(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response termFreq(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response field(ApiVersion apiVersion, String corpusName, String fieldName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response status(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response autocompleteMetadata(ApiVersion apiVersion, String corpusName, String fieldName,
            MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response autocompleteAnnotated(ApiVersion apiVersion, String corpusName, String fieldName, String annotationName,
            MultivaluedMap<String, String> parameters, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response serverInfo(ApiVersion apiVersion, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response listInputFormats(ApiVersion apiVersion, MultivaluedMap<String, String> parameters, String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response inputFormat(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Response inputFormatXslt(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

}
