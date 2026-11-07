package com.agrimarket.api;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.UserRole;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.live.LiveUpdateBroker;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events stream of "data changed" signals for dashboards.
 *
 * <p>Authenticated with the normal {@code Authorization: Bearer} JWT (the web client reads the stream
 * with {@code fetch}, so no token ever goes into a URL). Events carry only a type and provider id; the
 * page re-fetches via its usual endpoints.
 */
@RestController
@RequestMapping("/api/me/live-updates")
@RequiredArgsConstructor
public class LiveUpdatesController {

    private static final Set<UserRole> ALLOWED = EnumSet.of(
            UserRole.PLATFORM_ADMIN,
            UserRole.SUPPORT,
            UserRole.PROVIDER_OWNER,
            UserRole.PROVIDER_ADMIN,
            UserRole.PROVIDER_STAFF,
            UserRole.PROVIDER_VIEWER);

    private final LiveUpdateBroker broker;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@AuthenticationPrincipal MarketUserPrincipal actor) {
        if (actor == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH", "Unauthorized");
        }
        if (!ALLOWED.contains(actor.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LIVE", "Live updates are not available for this account");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().noTransform())
                // Disable response buffering in nginx-style proxies so events arrive immediately.
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(broker.subscribe(actor));
    }
}
