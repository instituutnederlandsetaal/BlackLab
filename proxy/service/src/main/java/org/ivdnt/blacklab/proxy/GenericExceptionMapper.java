package org.ivdnt.blacklab.proxy;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.ext.ExceptionMapper;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivdnt.blacklab.proxy.logic.Requests;
import org.ivdnt.blacklab.proxy.representation.ErrorResponse;
import org.ivdnt.blacklab.proxy.resources.ProxyResponse;

/**
 * A catch-all for exceptions.
 * This class has specialized handling for some common exceptions such as when a requested CorpusFile does not exist.
 * The generated response will either contain a standard ResponseMessage detailing the error when the client requested JSON/XML/text,
 * 	or will contain the standard tomcat-generated page when the client requested HTML.
 */
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

	@Context
	private HttpHeaders headers;

	private static Set<MediaType> supportedMediaTypes = new HashSet<>(Arrays.asList(
			MediaType.WILDCARD_TYPE,
			MediaType.APPLICATION_JSON_TYPE,
			MediaType.APPLICATION_XML_TYPE,
			MediaType.TEXT_HTML_TYPE,
			MediaType.TEXT_PLAIN_TYPE));

	@Override
	public Response toResponse(Exception exception) {
	    
	    exception.printStackTrace();
	    
		MediaType responseType = getAcceptType();
		ResponseMessage message = null;
		ResponseBuilder response = null;

		// Handle standard jersey exceptions, these are thrown when a page with an invalid url is requested,
		//	a page parameter contains an invalid value, etc.
        if (exception instanceof Requests.BlsRequestException) {
            Requests.BlsRequestException blsEx = (Requests.BlsRequestException) exception;
            ErrorResponse.Desc err = blsEx.getResponse().getError();
            return ProxyResponse.error(blsEx.getStatus(), err.getCode(), err.getMessage(), err.getStackTrace());
        } else if (exception instanceof WebApplicationException) {
			WebApplicationException appEx = (WebApplicationException) exception;

			// Preserve the original response, containing (among others) the http status code.
			response = Response.fromResponse(appEx.getResponse());
			message = new ResponseMessage("Message", appEx);

        } else if (exception instanceof IllegalArgumentException) {
            message = new ResponseMessage("Bad request", exception);
            response = Response.status(Response.Status.BAD_REQUEST);
		} else {
			// If we're here, this is some special exception
			// 	serve up a reply containing some debug information

			response = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
			message = new ResponseMessage(
						"An unhandled exception was thrown during operation (See GenericExceptionMapper.java)\n\n" +
						"Message: " + exception.getMessage() + "\n\n" + ExceptionUtils.getStackTrace(exception),
						ExceptionUtils.getStackTrace(exception));

			exception.printStackTrace();
		}

		// By default every response without entity generates an html page
		// So if the client requested html, don't set the entity, and tomcat will generate a standard page detailing the error.
		if (!responseType.equals(MediaType.TEXT_HTML_TYPE))
			response.entity(message);

		return response.type(responseType).build();
	}

	private MediaType getAcceptType(){
		// These are sorted with the most-suitable type first
		List<MediaType> accepts = headers.getAcceptableMediaTypes();

         // Find the first type we can produce, or default to JSON
         MediaType producedType = MediaType.APPLICATION_JSON_TYPE;
         for (MediaType type : accepts) {
             MediaType typeWithoutParam = new MediaType(type.getType(), type.getSubtype()); // strip off q-value, etc.
        	 if (supportedMediaTypes.contains(typeWithoutParam)) {
        		 producedType = typeWithoutParam;
        		 break;
        	 }
         }

         // If the client accepts any type, JSON it is
         if (producedType.equals(MediaType.WILDCARD_TYPE))
        	 producedType = MediaType.APPLICATION_JSON_TYPE;

         return producedType;
    }
}
