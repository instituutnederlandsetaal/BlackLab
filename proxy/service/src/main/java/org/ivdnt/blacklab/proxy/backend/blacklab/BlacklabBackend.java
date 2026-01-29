package org.ivdnt.blacklab.proxy.backend.blacklab;

import java.util.List;
import java.util.Map;

import org.ivdnt.blacklab.proxy.backend.Backend;
import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.AnnotatedField;
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
import org.ivdnt.blacklab.proxy.representation.MetadataField;
import org.ivdnt.blacklab.proxy.representation.ParsePatternResponse;
import org.ivdnt.blacklab.proxy.representation.RelationsResponse;
import org.ivdnt.blacklab.proxy.representation.Server;
import org.ivdnt.blacklab.proxy.representation.TermFreqList;
import org.ivdnt.blacklab.proxy.representation.TokenFreqList;
import org.ivdnt.blacklab.proxy.resources.ParamsUtil;
import org.ivdnt.blacklab.proxy.resources.SimpleResponse;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.server.lib.results.WebserviceOperations;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

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
    public Response hits(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.HITS_CSV : WebserviceOperation.HITS;
        List<Class<?>> resultTypes = isCsv ? List.of(JsonCsvResponse.class) : List.of(TokenFreqList.class, HitsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(null, method, corpusName, parameters, op, resultTypes, isXml);
    }

    @Override
    public Response docs(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.DOCS_CSV : WebserviceOperation.DOCS;
        List<Class<?>> resultTypes = List.of(isCsv ? JsonCsvResponse.class : DocsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(null, method, corpusName, parameters, op, resultTypes, isXml);
    }

    @Override
    public Response parsePattern(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.PARSE_PATTERN);
        return SimpleResponse.success(Requests.request(null, params, method, ParsePatternResponse.class));
    }

    @Override
    public Response relations(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.RELATIONS);
        return SimpleResponse.success(Requests.request(null, params, method, RelationsResponse.class));
    }

    @Override
    public Response docInfo(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_INFO);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return SimpleResponse.success(Requests.request(null, params, method, DocInfoResponse.class));
    }

    @Override
    public Response docContents(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.DOC_CONTENTS);
        params.put(WebserviceParameter.DOC_PID, docPid);
        DocContentsResults entity = Requests.request(null, params, method, DocContentsResults.class);
        return Response.ok().entity(entity.contents).type(MediaType.APPLICATION_XML).build();
    }

    @Override
    public Response docSnippet(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_SNIPPET);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return SimpleResponse.success(Requests.request(null, params, method, DocSnippetResponse.class));
    }

    @Override
    public Response termFreq(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters, String method) {
        return SimpleResponse.success(Requests.request(null, ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.TERM_FREQUENCIES), method, TermFreqList.class));
    }

    @Override
    public Response field(ApiVersion apiVersion, String corpusName, String fieldName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.FIELD_INFO);
        params.put(WebserviceParameter.FIELD, fieldName);
        return SimpleResponse.success(
                Requests.request(null, params, method, List.of(MetadataField.class, AnnotatedField.class)));
    }

    @Override
    public Response status(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        return SimpleResponse.success(Requests.request(null, ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.CORPUS_STATUS), method, CorpusStatus.class));
    }

    @Override
    public Response autocompleteMetadata(ApiVersion apiVersion, String corpusName, String fieldName,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        return SimpleResponse.success(
                Requests.request(null, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    @Override
    public Response autocompleteAnnotated(ApiVersion apiVersion, String corpusName, String fieldName, String annotationName,
            MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        params.put(WebserviceParameter.ANNOTATION, annotationName);
        return SimpleResponse.success(
                Requests.request(null, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    @Override
    public Response serverInfo(ApiVersion apiVersion, String method) {
        Map<WebserviceParameter, String> params = Map.of(WebserviceParameter.OPERATION,
                WebserviceOperation.SERVER_INFO.value(), WebserviceParameter.API_VERSION, apiVersion.toString());
        return SimpleResponse.success(
                Requests.request(null, params, method, Server.class));
    }

    @Override
    public Response listInputFormats(ApiVersion apiVersion, MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.LIST_INPUT_FORMATS);
        InputFormats entity = Requests.request(null, params, method, InputFormats.class);
        return Response.ok().entity(entity).build();
    }

    @Override
    public Response inputFormat(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_INFO);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatInfo entity = Requests.request(null, params, method, InputFormatInfo.class);
        return Response.ok().entity(entity).build();
    }

    @Override
    public Response inputFormatXslt(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_XSLT);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatXsltResults entity = Requests.request(null, params, method, InputFormatXsltResults.class);
        return Response.ok().entity(entity.xslt).type(MediaType.APPLICATION_XML).build();
    }

}
