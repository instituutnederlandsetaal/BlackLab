package org.ivdnt.blacklab.proxy;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;

/**
 * If a parameter "outputformat" was specified, override the Accept header
 * with the requested output type. Handy for easier testing in browser.
 */
@Provider
@PreMatching
public class OutputTypeFilter implements ContainerRequestFilter {
 
    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        UriInfo uriInfo = ctx.getUriInfo();
        MultivaluedMap<String, String> param = uriInfo.getQueryParameters();
        List<String> parOutput = param.get("outputformat");
        if (parOutput != null && !parOutput.isEmpty()) {
            String outputType = parOutput.get(0);
            switch (outputType) {
            case "xml":
                ctx.getHeaders().replace("Accept", Arrays.asList("application/xml"));
                break;
            case "json":
                ctx.getHeaders().replace("Accept", Arrays.asList("application/json"));
                break;
            case "html":
                ctx.getHeaders().replace("Accept", Arrays.asList("text/html"));
                break;
            default:
                // Ignore unknown output types
                break;
            }
        }
    }
}
