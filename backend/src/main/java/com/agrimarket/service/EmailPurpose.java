package com.agrimarket.service;

public enum EmailPurpose {
    INFO("info"),
    SUPPORT("support"),
    SECURITY("security"),
    BILLING("billing"),
    NO_REPLY("no-reply");

    private final String localPart;

    EmailPurpose(String localPart) {
        this.localPart = localPart;
    }

    public String localPart() {
        return localPart;
    }
}
