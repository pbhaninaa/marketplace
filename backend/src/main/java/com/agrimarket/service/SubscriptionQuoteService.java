package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.BillingCycle;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.SubscriptionActivationIntent;
import com.agrimarket.domain.SubscriptionPlan;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.repo.SubscriptionActivationIntentRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionQuoteService {

    private final SubscriptionActivationIntentRepository intentRepository;
    private final ProviderRepository providerRepository;
    private final SubscriptionUsageBilling usageBilling;

    @Transactional
    public SubscriptionActivationIntent createQuote(Long providerId, SubscriptionPlan plan) {
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBSCRIPTION", "Plan is required");
        }
        Provider p = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));

        Instant now = Instant.now();
        var inv = usageBilling.computeFirstMonthInvoice(providerId, plan, now);

        SubscriptionActivationIntent intent = new SubscriptionActivationIntent();
        intent.setProvider(p);
        intent.setPlan(plan);
        intent.setBillingCycle(BillingCycle.MONTHLY);
        intent.setAmountDue(inv.totalDue());
        intent.setBaseMonthly(inv.baseMonthly());
        intent.setUsageFeePercent(inv.usageFeePercent());
        intent.setPaidTransactionsCount(inv.paidTransactionsCounted());
        intent.setPaidOrderTotalsSum(inv.paidOrderTotalsSum());
        intent.setUsageFeesTotal(inv.usageFeesTotal());
        intent.setUsageWindowStart(inv.usageWindowStart());
        intent.setPaymentReference(generatePaymentReference(providerId));
        intent.setUsed(false);
        return intentRepository.save(intent);
    }

    @Transactional(readOnly = true)
    public SubscriptionActivationIntent requireIntent(Long providerId, Long intentId) {
        return intentRepository
                .findByIdAndProviderId(intentId, providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBSCRIPTION", "Payment quote not found"));
    }

    private String generatePaymentReference(Long providerId) {
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "SUB-" + providerId + "-" + shortId;
    }
}
