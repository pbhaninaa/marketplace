package com.agrimarket.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Fields a signed-in user may change about themselves. Blank values clear the field. */
public record ProfileUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 200) String displayName,
        @Size(max = 40) @Pattern(regexp = "^[+0-9 ()-]*$", message = "may only contain digits, spaces, +, - and brackets")
                String phoneNumber) {}
