package nl.inl.blacklab.server;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Used to forward static resource URLs to the default servlet.
 *
 * From: http://www.kuligowski.pl/java/rest-style-urls-and-url-mapping-for-static-content-apache-tomcat,5
 */
public class DefaultFilter implements Filter {

    private RequestDispatcher defaultRequestDispatcher;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest req) {
            String url = req.getPathInfo();
            if (url.equals("/search-test/")) {
                if (response instanceof HttpServletResponse resp) {
                    resp.setStatus(302); // 302 Found
                    resp.setHeader("Location", req.getRequestURL() + "index.html");
                    return;
                }
            }
        }

        defaultRequestDispatcher.forward(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        this.defaultRequestDispatcher = filterConfig.getServletContext().getNamedDispatcher("default");
    }

    @Override
    public void destroy() {
        // NOP
    }
}
