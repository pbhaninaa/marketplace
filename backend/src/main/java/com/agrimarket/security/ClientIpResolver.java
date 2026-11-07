package com.agrimarket.security;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Works out the caller's IP behind reverse proxies (Railway's edge, nginx, load balancers).
 *
 * <p>Proxies append the address they received the request from to {@code X-Forwarded-For}, so only
 * the right-most entries are trustworthy; anything to the left can be set by the client. With
 * {@code trustedProxyHops = 1} (one proxy in front of the API, e.g. Railway) the right-most entry is
 * used. With 0 the header is ignored and the socket address is used.
 *
 * <p>UAT/PROD run with {@code server.forward-headers-strategy=framework}: Spring's
 * ForwardedHeaderFilter then hides {@code X-Forwarded-For} and reports its <em>left-most</em>
 * (client-controlled) entry as {@code getRemoteAddr()}. To avoid trusting that, we read the header
 * and socket address from the original container request underneath any wrappers.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request, int trustedProxyHops) {
        ServletRequest raw = unwrap(request);
        String remote = raw.getRemoteAddr();
        if (trustedProxyHops <= 0) return remote;
        String xff = raw instanceof HttpServletRequest http ? http.getHeader("X-Forwarded-For") : null;
        if (xff == null || xff.isBlank()) return remote;
        String[] parts = xff.split(",");
        int idx = parts.length - trustedProxyHops;
        if (idx < 0) idx = 0;
        String ip = parts[idx].trim();
        return ip.isEmpty() ? remote : ip;
    }

    /** The innermost (container) request, so wrapper-rewritten headers / remote address are ignored. */
    static ServletRequest unwrap(ServletRequest request) {
        ServletRequest current = request;
        int guard = 0;
        while (current instanceof ServletRequestWrapper wrapper && guard++ < 50) {
            current = wrapper.getRequest();
        }
        return current;
    }
}
