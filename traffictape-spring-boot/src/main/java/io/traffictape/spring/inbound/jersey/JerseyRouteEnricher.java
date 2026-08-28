package io.traffictape.spring.inbound.jersey;

import io.traffictape.spring.CaptureContexts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/**
 * After Jersey matches a resource, stashes the {@code @Path} template on the servlet
 * request so {@code InboundTrafficTapeFilter} records {@code /orders/{id}} instead of
 * {@code /orders/99}. Does not record on its own — that would double-count.
 */
@Provider
public final class JerseyRouteEnricher implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        try {
            if (servletRequest == null) {
                return;
            }
            String route = JerseyPaths.template(resourceInfo);
            if (route != null) {
                servletRequest.setAttribute(CaptureContexts.ROUTE_ATTRIBUTE, route);
            }
        } catch (Throwable ignored) {
        }
    }
}
