package com.agrimarket.api.dto;

import java.util.List;

/** Status polled by the guest return page after a Peach Hosted Checkout redirect. */
public record PeachStatusResponse(
        String status,
        List<Long> orderIds,
        List<Long> rentalBookingIds,
        List<String> verificationCodes) {}
