package com.agrimarket.api.dto;

/**
 * @param signupEmailVerificationRequired provider sign-up needs a code emailed to the owner address.
 */
public record SetupStatusResponse(boolean needsFirstAdmin, boolean signupEmailVerificationRequired) {}
