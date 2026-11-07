package com.agrimarket.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static MockHttpServletRequest req(String xff) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("10.0.0.1");
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        return r;
    }

    @Test
    void usesSocketAddressWithoutHeaderOrWhenNoProxiesTrusted() {
        assertThat(ClientIpResolver.resolve(req(null), 1)).isEqualTo("10.0.0.1");
        assertThat(ClientIpResolver.resolve(req("203.0.113.5"), 0)).isEqualTo("10.0.0.1");
    }

    @Test
    void takesTheEntryAddedByTheTrustedProxies() {
        // client-supplied junk on the left, real client added by our proxy (Railway edge) on the right
        assertThat(ClientIpResolver.resolve(req("6.6.6.6, 203.0.113.5"), 1)).isEqualTo("203.0.113.5");
        // load balancer + nginx: LB appends client, nginx appends LB
        assertThat(ClientIpResolver.resolve(req("6.6.6.6, 203.0.113.5, 172.31.0.9"), 2)).isEqualTo("203.0.113.5");
        // fewer entries than hops: use the left-most we have
        assertThat(ClientIpResolver.resolve(req("203.0.113.5"), 3)).isEqualTo("203.0.113.5");
    }

    /**
     * With server.forward-headers-strategy=framework (UAT/PROD), Spring's ForwardedHeaderFilter wraps
     * the request, hides X-Forwarded-For and reports its left-most, client-controlled entry as the
     * remote address. The resolver must read the original request instead.
     */
    @Test
    void ignoresWrappersThatHideOrRewriteForwardedHeaders() {
        MockHttpServletRequest raw = req("6.6.6.6, 203.0.113.5");
        HttpServletRequestWrapper likeForwardedHeaderFilter = new HttpServletRequestWrapper(raw) {
            @Override
            public String getRemoteAddr() {
                return "6.6.6.6";
            }

            @Override
            public String getHeader(String name) {
                return "X-Forwarded-For".equalsIgnoreCase(name) ? null : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return "X-Forwarded-For".equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
            }
        };
        assertThat(ClientIpResolver.resolve(new HttpServletRequestWrapper(likeForwardedHeaderFilter), 1))
                .isEqualTo("203.0.113.5");
        assertThat(ClientIpResolver.resolve(likeForwardedHeaderFilter, 0)).isEqualTo("10.0.0.1");
    }
}
