package com.agrimarket.security;

/**
 * Single list of request paths and their URL-level access rules. Anything not listed here requires
 * authentication; finer ownership / permission checks live in {@link AccessGuard}.
 */
public final class SecurityPaths {

    private SecurityPaths() {}

    /** Reachable without a token: health checks, the public marketplace / cart / guest checkout, login. */
    public static final String[] PUBLIC = {
        "/actuator/health",
        "/actuator/health/**",
        "/api/public/**",
        "/api/auth/login",
    };

    public static final String ADMIN = "/api/admin/**";

    public static final String SUPPORT = "/api/support/**";

    public static final String PROVIDER = "/api/provider/**";

    public static final String[] SUPPORT_ROLES = {"PLATFORM_ADMIN", "SUPPORT"};

    public static final String[] PROVIDER_ROLES = {
        "PROVIDER_OWNER", "PROVIDER_ADMIN", "PROVIDER_STAFF", "PROVIDER_VIEWER",
    };
}
