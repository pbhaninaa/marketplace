package com.agrimarket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientOtpRequest(@NotBlank @Size(max = 200) String target) {}

