package com.agrimarket.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ask for a one-time code proving ownership of the owner email before provider sign-up. */
public record SignupEmailCodeRequest(@NotBlank @Email @Size(max = 200) String email) {}
