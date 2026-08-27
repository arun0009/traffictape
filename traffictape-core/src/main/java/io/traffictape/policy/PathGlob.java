package io.traffictape.policy;

/**
 * Minimal glob: {@code *} matches within a segment, {@code **} matches across segments.
 */
public final class PathGlob {

    private PathGlob() {
    }

    public static boolean matches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        String p = pattern;
        String t = path;
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        if (p.endsWith("/**")) {
            String prefix = p.substring(0, p.length() - 3);
            return t.equals(prefix) || t.startsWith(prefix + "/") || t.startsWith(prefix);
        }
        return glob(p, t);
    }

    private static boolean glob(String pattern, String text) {
        int p = 0;
        int t = 0;
        int star = -1;
        int match = 0;
        while (t < text.length()) {
            if (p < pattern.length() && (pattern.charAt(p) == '?' || pattern.charAt(p) == text.charAt(t))) {
                p++;
                t++;
            } else if (p < pattern.length() && pattern.charAt(p) == '*') {
                star = p++;
                match = t;
            } else if (star != -1) {
                p = star + 1;
                t = ++match;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
