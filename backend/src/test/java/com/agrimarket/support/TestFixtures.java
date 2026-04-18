package com.agrimarket.support;

import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Category;
import com.agrimarket.domain.Listing;
import com.agrimarket.domain.ListingType;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.ProviderStatus;
import com.agrimarket.domain.Subscription;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.domain.SubscriptionStatus;
import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ListingRepository;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestFixtures {

    private final CategoryRepository categoryRepository;
    private final ProviderRepository providerRepository;
    private final ListingRepository listingRepository;
    private final SubscriptionRepository subscriptionRepository;

    public Category saveCategory(String name, String slug) {
        return categoryRepository.save(new Category(name, slug));
    }

    public Provider saveActiveProvider(String name, String slug) {
        Provider p = new Provider(name, slug, "Test provider", "Testville");
        p.setStatus(ProviderStatus.ACTIVE);
        return providerRepository.save(p);
    }

    public void saveActiveSubscription(Provider provider) {
        Subscription s = new Subscription();
        s.setProvider(provider);
        s.setPlan(SubscriptionPlan.BASIC);
        s.setBillingCycle(BillingCycle.MONTHLY);
        s.setStatus(SubscriptionStatus.ACTIVE);
        s.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptionRepository.save(s);
    }

    public Listing saveSaleListing(Provider provider, Category category, String title, BigDecimal price, int stock) {
        Listing l = new Listing();
        l.setProvider(provider);
        l.setCategory(category);
        l.setListingType(ListingType.SALE);
        l.setTitle(title);
        l.setDescription("Integration test listing");
        l.setUnitPrice(price);
        l.setStockQuantity(stock);
        l.setActive(true);
        return listingRepository.save(l);
    }
}
