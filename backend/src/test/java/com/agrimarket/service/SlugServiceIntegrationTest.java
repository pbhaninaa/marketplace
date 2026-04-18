package com.agrimarket.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrimarket.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SlugServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SlugService slugService;

    @Test
    void uniqueSlug_slugifiesBusinessName() {
        assertThat(slugService.uniqueProviderSlug("Green Valley & Co.")).matches("green-valley-and-co");
    }
}
