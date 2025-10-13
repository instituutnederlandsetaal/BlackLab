package org.ivdnt.blacklab.proxy.resources;

import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.AnnotatedField;
import org.ivdnt.blacklab.proxy.representation.AutocompleteResponse;
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

import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WebserviceParameter;

public class ProxyRequest {

    public static Response hits(Client client, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.HITS_CSV : WebserviceOperation.HITS;
        List<Class<?>> resultTypes = isCsv ? List.of(JsonCsvResponse.class) : List.of(TokenFreqList.class, HitsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(client, method, corpusName, parameters, op, resultTypes, isXml);
    }

    public static Response docs(Client client, String corpusName, MultivaluedMap<String, String> parameters,
            HttpHeaders headers, String method) {
        boolean isCsv = ParamsUtil.isCsvRequest(headers);
        WebserviceOperation op = isCsv ? WebserviceOperation.DOCS_CSV : WebserviceOperation.DOCS;
        List<Class<?>> resultTypes = List.of(isCsv ? JsonCsvResponse.class : DocsResults.class);
        boolean isXml = !isCsv && !headers.getAcceptableMediaTypes().contains(MediaType.APPLICATION_JSON_TYPE);
        return Requests.requestWithPossibleCsvResponse(client, method, corpusName, parameters, op, resultTypes, isXml);
    }

    static Response parsePattern(Client client, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.PARSE_PATTERN);
        return ProxyResponse.success(Requests.request(client, params, method, ParsePatternResponse.class));
    }

    static Response relations(Client client, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.RELATIONS);
        return ProxyResponse.success(Requests.request(client, params, method, RelationsResponse.class));
    }

    static Response docInfo(Client client, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_INFO);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return ProxyResponse.success(Requests.request(client, params, method, DocInfoResponse.class));
    }

    public static Response docContents(Client client, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.DOC_CONTENTS);
        params.put(WebserviceParameter.DOC_PID, docPid);
        DocContentsResults entity = Requests.request(client, params, method, DocContentsResults.class);
        return Response.ok().entity(entity.contents).type(MediaType.APPLICATION_XML).build();
    }

    public static Response docSnippet(Client client, String corpusName, String docPid, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.DOC_SNIPPET);
        params.put(WebserviceParameter.DOC_PID, docPid);
        return ProxyResponse.success(Requests.request(client, params, method, DocSnippetResponse.class));
    }

    public static Response termFreq(Client client, String corpusName, MultivaluedMap<String, String> parameters, String method) {
        return ProxyResponse.success(Requests.request(client, ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.TERM_FREQUENCIES), method, TermFreqList.class));
    }

    public static Response field(Client client, String corpusName, String fieldName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.FIELD_INFO);
        params.put(WebserviceParameter.FIELD, fieldName);
        return ProxyResponse.success(
                Requests.request(client, params, method, List.of(MetadataField.class, AnnotatedField.class)));
    }

    public static Response status(Client client, String corpusName, MultivaluedMap<String, String> parameters,
            String method) {
        return ProxyResponse.success(Requests.request(client, ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.CORPUS_STATUS), method, CorpusStatus.class));
    }

    public static Response autocompleteMetadata(Client client, String corpusName, String fieldName, MultivaluedMap<String, String> parameters,
            String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName,
                WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        return ProxyResponse.success(
                Requests.request(client, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    public static Response autocompleteAnnotated(Client client, String corpusName, String fieldName, String annotationName,
            MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters, corpusName, WebserviceOperation.AUTOCOMPLETE);
        params.put(WebserviceParameter.FIELD, fieldName);
        params.put(WebserviceParameter.ANNOTATION, annotationName);
        return ProxyResponse.success(
                Requests.request(client, params, method, List.of(AutocompleteResponse.class, List.class)));
    }

    public static Response serverInfo(Client client, String api, String method) {
        Map<WebserviceParameter, String> params = Map.of(WebserviceParameter.OPERATION,
                WebserviceOperation.SERVER_INFO.value(), WebserviceParameter.API_VERSION, api);
        return ProxyResponse.success(Requests.request(client, params, method, Server.class));
    }

    public static Response listInputFormats(Client client, MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.LIST_INPUT_FORMATS);
        InputFormats entity = Requests.request(client, params, method, InputFormats.class);
        return Response.ok().entity(entity).build();
    }

    public static Response inputFormat(Client client, String formatName, MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_INFO);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatInfo entity = Requests.request(client, params, method, InputFormatInfo.class);
        return Response.ok().entity(entity).build();
    }

    public static Response inputFormatXslt(Client client, String formatName, MultivaluedMap<String, String> parameters, String method) {
        Map<WebserviceParameter, String> params = ParamsUtil.get(parameters,
                WebserviceOperation.INPUT_FORMAT_XSLT);
        params.put(WebserviceParameter.INPUT_FORMAT, formatName);
        InputFormatXsltResults entity = Requests.request(client, params, method, InputFormatXsltResults.class);
        return Response.ok().entity(entity.xslt).type(MediaType.APPLICATION_XML).build();
    }
}
