package com.agrimarket.api.dto;

import com.agrimarket.domain.AppNotificationType;
import java.time.Instant;

public record AppNotificationResponse(
        Long id,
        AppNotificationType notificationType,
        String title,
        String body,
        String linkPath,
        boolean read,
        Instant createdAt) {}
