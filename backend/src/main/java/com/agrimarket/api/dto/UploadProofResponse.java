package com.agrimarket.api.dto;

import com.agrimarket.domain.SubscriptionProofStatus;
import java.time.Instant;

public record UploadProofResponse(Long proofId, SubscriptionProofStatus status, Instant createdAt) {}

