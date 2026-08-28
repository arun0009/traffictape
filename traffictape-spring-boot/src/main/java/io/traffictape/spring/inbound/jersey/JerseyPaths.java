package io.traffictape.spring.inbound.jersey;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceInfo;

import java.lang.reflect.Method;

/**
 * Builds a route template from JAX-RS {@code @Path} on the matched class and method.
 */
public final class JerseyPaths {

    private JerseyPaths() {
    }

    public static String template(ResourceInfo info) {
        if (info == null) {
            return null;
        }
        return template(info.getResourceClass(), info.getResourceMethod());
    }

    static String template(Class<?> type, Method method) {
        if (type == null) {
            return null;
        }
        return join(value(type.getAnnotation(Path.class)), method == null ? "" : value(method.getAnnotation(Path.class)));
    }

    private static String value(Path path) {
        return path == null || path.value() == null ? "" : path.value().trim();
    }

    private static String join(String classPath, String methodPath) {
        String left = trimSlashes(classPath);
        String right = trimSlashes(methodPath);
        if (left.isEmpty() && right.isEmpty()) {
            return "/";
        }
        if (right.isEmpty()) {
            return "/" + left;
        }
        if (left.isEmpty()) {
            return "/" + right;
        }
        return "/" + left + "/" + right;
    }

    private static String trimSlashes(String path) {
        String s = path;
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
