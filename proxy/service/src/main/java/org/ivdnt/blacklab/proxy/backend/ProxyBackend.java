package org.ivdnt.blacklab.proxy.backend;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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
import org.ivdnt.blacklab.proxy.resources.ProxyParamsUtil;
import org.ivdnt.blacklab.proxy.resources.SimpleResponse;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import nl.inl.blacklab.server.lib.requests.RequestCorpusInfo;
import nl.inl.blacklab.server.lib.requests.RequestFieldInfo;
import nl.inl.blacklab.server.lib.results.ApiVersion;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/** A backend that reverse proxies another BlackLab Server instance */
public class ProxyBackend implements Backend {

    /** REST client to forward requests to the BlackLab instance we're proxying */
    private final Client client;

    public ProxyBackend(Client client) {
        this.client = client;
    }

    @Override
    public Response corpusInfo(ApiVersion apiVersion, RequestCorpusInfo req) {
        Map<WsParam, String> params = Map.of(
                WsParam.CORPUS_NAME, req.corpusName(),
                WsParam.OPERATION, WebserviceOperation.CORPUS_INFO.toString(),
                WsParam.LIST_VALUES_FOR_ANNOTATIONS, StringUtils.join(req.listValuesFor(), ","),
                WsParam.LIMIT_VALUES, Long.toString(req.limitValues())
        );
        return SimpleResponse.success(Requests.get(this.client, params, Corpus.class));
    }

    @Override
    public Response field(ApiVersion apiVersion, RequestFieldInfo req) {
        Map<WebserviceParameter, String> params = Map.of(
                WebserviceParameter.CORPUS_NAME, req.corpusName(),
                WebserviceParameter.OPERATION, WebserviceOperation.FIELD_INFO.toString(),
                WebserviceParameter.FIELD, req.fieldName()
        );
        return SimpleResponse.success(
                Requests.request(this.client, params, req.method(),
                        List.of(MetadataField.class, AnnotatedField.class)));
    }

    @Override
    public Response hits(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ProxyParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.HITS_CSV : WebserviceOperation.HITS;
        List<Class<?>> resultTypes = isCsv ? List.of(JsonCsvResponse.class) : List.of(TokenFreqList.class, HitsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(this.client, method, corpusName, parameters, op, resultTypes, isXml);
    }

    @Override
    public Response docs(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ProxyParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.DOCS_CSV : WebserviceOperation.DOCS;
        List<Class<?>> resultTypes = List.of(isCsv ? JsonCsvResponse.class : DocsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(this.client, method, corpusName, parameters, op, resultTypes, isXml);
    }

    @Override
    public Response parsePattern(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.PARSE_PATTERN);
        return SimpleResponse.success(Requests.request(this.client, params, method, ParsePatternResponse.class));
    }

    @Override
    public Response relations(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.RELATIONS);
        return SimpleResponse.success(Requests.request(this.client, params, method, RelationsResponse.class));
    }

    @Override
    public Response docInfo(ApiVersion apiVersion, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_INFO);
        params.put(WsParam.DOC_PID, docPid);
        return SimpleResponse.success(Requests.request(this.client, params, method, DocInfoResponse.class));
    }

    @Override
    public Response docContents(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName,
                WebserviceOperation.DOC_CONTENTS);
        params.put(WsParam.DOC_PID, docPid);
        DocContentsResults entity = Requests.request(this.client, params, method, DocContentsResults.class);
        return Response.ok().entity(entity.contents).type(MediaType.APPLICATION_XML).build();
    }

    @Override
    public Response docSnippet(ApiVersion apiVersion, String corpusName, String docPid,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_SNIPPET);
        params.put(WsParam.DOC_PID, docPid);
        return SimpleResponse.success(Requests.request(this.client, params, method, DocSnippetResponse.class));
    }

    @Override
    public Response termFreq(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters, String method) {
        return SimpleResponse.success(Requests.request(this.client, ProxyParamsUtil.get(parameters, corpusName,
                WebserviceOperation.TERM_FREQUENCIES), method, TermFreqList.class));
    }

    @Override
    public Response field(ApiVersion apiVersion, String corpusName, String fieldName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.FIELD_INFO);
        params.put(WsParam.FIELD, fieldName);
        return SimpleResponse.success(
                Requests.request(this.client, params, method, List.of(MetadataField.class, AnnotatedField.class)));
    }

    @Override
    public Response status(ApiVersion apiVersion, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        return SimpleResponse.success(Requests.request(this.client, ProxyParamsUtil.get(parameters, corpusName,
                WebserviceOperation.CORPUS_STATUS), method, CorpusStatus.class));
    }

    @Override
    public Response autocompleteMetadata(ApiVersion apiVersion, String corpusName, String fieldName,
            MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName,
                WebserviceOperation.AUTOCOMPLETE);
        params.put(WsParam.FIELD, fieldName);
        return SimpleResponse.success(
                Requests.request(this.client, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    @Override
    public Response autocompleteAnnotated(ApiVersion apiVersion, String corpusName, String fieldName, String annotationName,
            MultivaluedMap<String, String> parameters, String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters, corpusName, WebserviceOperation.AUTOCOMPLETE);
        params.put(WsParam.FIELD, fieldName);
        params.put(WsParam.ANNOTATION, annotationName);
        return SimpleResponse.success(
                Requests.request(this.client, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    @Override
    public Response serverInfo(ApiVersion apiVersion, String method) {
        Map<WsParam, String> params = Map.of(WsParam.OPERATION,
                WebserviceOperation.SERVER_INFO.value(), WsParam.API, apiVersion.toString());
        return SimpleResponse.success(
                Requests.request(this.client, params, method, Server.class));
    }

    @Override
    public Response listInputFormats(ApiVersion apiVersion, MultivaluedMap<String, String> parameters, String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters,
                WebserviceOperation.LIST_INPUT_FORMATS);
        InputFormats entity = Requests.request(this.client, params, method, InputFormats.class);
        return Response.ok().entity(entity).build();
    }

    @Override
    public Response inputFormat(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_INFO);
        params.put(WsParam.INPUT_FORMAT, formatName);
        InputFormatInfo entity = Requests.request(this.client, params, method, InputFormatInfo.class);
        return Response.ok().entity(entity).build();
    }

    @Override
    public Response inputFormatXslt(ApiVersion apiVersion, String formatName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WsParam, String> params = ProxyParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_XSLT);
        params.put(WsParam.INPUT_FORMAT, formatName);
        InputFormatXsltResults entity = Requests.request(this.client, params, method, InputFormatXsltResults.class);
        return Response.ok().entity(entity.xslt).type(MediaType.APPLICATION_XML).build();
    }

}
