package org.ivdnt.blacklab.proxy;

import java.io.IOException;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.ivdnt.blacklab.proxy.representation.ErrorResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

@Provider
public class ErrorResponseFilter implements ClientResponseFilter {

    private static ObjectMapper _MAPPER = new ObjectMapper();

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        // for non-200 response, deal with the custom error messages
        if (responseContext.getStatus() != Response.Status.OK.getStatusCode()) {
            if (responseContext.hasEntity()) {
                // get the "real" error message
                ErrorResponse error = null;
                try {
                    error = _MAPPER.readValue(responseContext.getEntityStream(), ErrorResponse.class);
                } catch (IOException e) {
                    throw new InternalServerErrorException("Error while handling another error", e);
                }
                String message = error.getMessage();

                Response.Status status = Response.Status.fromStatusCode(responseContext.getStatus());
                WebApplicationException webAppException;
                switch (status) {
                case BAD_REQUEST:
                    webAppException = new BadRequestException(message);
                    break;
                case UNAUTHORIZED:
                    webAppException = new NotAuthorizedException(message);
                    break;
                case FORBIDDEN:
                    webAppException = new ForbiddenException(message);
                    break;
                case NOT_FOUND:
                    webAppException = new NotFoundException(message);
                    break;
                case METHOD_NOT_ALLOWED:
                    webAppException = new NotAllowedException(message);
                    break;
                case NOT_ACCEPTABLE:
                    webAppException = new NotAcceptableException(message);
                    break;
                case UNSUPPORTED_MEDIA_TYPE:
                    webAppException = new NotSupportedException(message);
                    break;
                case INTERNAL_SERVER_ERROR:
                    webAppException = new InternalServerErrorException(message);
                    break;
                case SERVICE_UNAVAILABLE:
                    webAppException = new ServiceUnavailableException(message);
                    break;
                default:
                    webAppException = new WebApplicationException(message);
                }

                throw webAppException;
            }
        }
    }
}
