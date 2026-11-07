package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminProofDecisionRequest(@NotNull Boolean approve, @Size(max = 500) String note) {}
