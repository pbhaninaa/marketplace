package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotNull;

public record AdminProofDecisionRequest(@NotNull Boolean approve, String note) {}

