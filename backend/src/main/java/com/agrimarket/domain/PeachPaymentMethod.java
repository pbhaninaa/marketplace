package com.agrimarket.domain;

/** Customer-selected payment method within Peach Hosted Checkout. */
public enum PeachPaymentMethod {
    CARD("CARD"),
    EFT("PAYBYBANK");

    private final String hostedCheckoutValue;

    PeachPaymentMethod(String hostedCheckoutValue) {
        this.hostedCheckoutValue = hostedCheckoutValue;
    }

    public String hostedCheckoutValue() {
        return hostedCheckoutValue;
    }
}
