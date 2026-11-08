package com.agrimarket.service;

import com.agrimarket.api.dto.SettlementDtos.PaymentSettlementRow;
import com.agrimarket.api.dto.SettlementDtos.PayoutActionResult;
import com.agrimarket.api.dto.SettlementDtos.ProviderPayoutsResponse;
import com.agrimarket.api.dto.SettlementDtos.SellerOwedRow;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.PaymentMethod;
import com.agrimarket.domain.PaymentRecord;
import com.agrimarket.domain.Provider;
import com.agrimarket.domain.SettlementStatus;
import com.agrimarket.repo.PaymentRecordRepository;
import com.agrimarket.repo.PlatformSettingsRepository;
import com.agrimarket.domain.PlatformSettings;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seller settlement for online (gateway) payments of orders and rentals. The platform collects the
 * money, keeps a platform fee and owes the rest to the seller until an admin records the payout.
 * Cash and manual EFT payments stay {@link SettlementStatus#NONE}: the buyer paid the seller directly.
 */
@Service
@RequiredArgsConstructor
public class SellerSettlementService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_ROWS = 500;

    private final PaymentRecordRepository paymentRecordRepository;
    private final PlatformSettingsRepository platformSettingsRepository;

    /** amount × percent / 100, rounded to cents HALF_UP. */
    public static BigDecimal platformFee(BigDecimal amount, BigDecimal percent) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal pct = percent == null ? BigDecimal.ZERO : percent;
        return amount.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal currentFeePercent() {
        return platformSettingsRepository
                .findById(PlatformSettingsService.SINGLETON_ID)
                .map(PlatformSettings::getOnlineOrderFeePercent)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Called (inside the gateway's transaction) when an online payment is confirmed. Records the fee
     * and the seller's share as HELD, or REFUND_DUE when the order / booking was already cancelled.
     * Idempotent: a payment that already has a settlement status is left alone.
     */
    public void recordConfirmedPayment(PaymentRecord payment, boolean cancelledBeforePayment) {
        if (payment == null || payment.getMethod() != PaymentMethod.PEACH) {
            return; // only online payments are collected by the platform
        }
        if (payment.getSettlementStatus() != SettlementStatus.NONE) {
            return;
        }
        BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
        BigDecimal percent = currentFeePercent();
        BigDecimal fee = platformFee(amount, percent);
        payment.setPlatformFeePercent(percent);
        payment.setPlatformFee(fee);
        payment.setSellerPayoutAmount(amount.subtract(fee).setScale(2, RoundingMode.HALF_UP));
        payment.setSettlementStatus(cancelledBeforePayment ? SettlementStatus.REFUND_DUE : SettlementStatus.HELD);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
    }

    // ---- Admin ----

    /** Amount owed per seller (HELD payments), largest first. */
    @Transactional(readOnly = true)
    public List<SellerOwedRow> owedBySeller() {
        List<PaymentRecord> held = paymentRecordRepository.findForSettlement(SettlementStatus.HELD, null, null, null);
        Map<Long, SellerOwedRow> bySeller = new LinkedHashMap<>();
        for (PaymentRecord pr : held) {
            Provider p = pr.getProvider();
            SellerOwedRow prev = bySeller.get(p.getId());
            BigDecimal owed = nz(pr.getSellerPayoutAmount());
            BigDecimal fee = nz(pr.getPlatformFee());
            BigDecimal gross = nz(pr.getAmount());
            if (prev == null) {
                bySeller.put(p.getId(), new SellerOwedRow(
                        p.getId(), p.getName(), p.getBankName(), p.getBankAccountName(),
                        p.getBankAccountNumber(), p.getBankBranchCode(), 1, gross, fee, owed));
            } else {
                bySeller.put(p.getId(), new SellerOwedRow(
                        prev.providerId(), prev.providerName(), prev.bankName(), prev.bankAccountName(),
                        prev.bankAccountNumber(), prev.bankBranchCode(), prev.paymentCount() + 1,
                        prev.grossAmount().add(gross), prev.platformFee().add(fee), prev.amountOwed().add(owed)));
            }
        }
        List<SellerOwedRow> out = new ArrayList<>(bySeller.values());
        out.sort((a, b) -> b.amountOwed().compareTo(a.amountOwed()));
        return out;
    }

    @Transactional(readOnly = true)
    public List<PaymentSettlementRow> list(SettlementStatus status, Long providerId, Instant from, Instant to) {
        return paymentRecordRepository.findForSettlement(status, providerId, from, to).stream()
                .limit(MAX_ROWS)
                .map(pr -> toRow(pr, true))
                .toList();
    }

    /**
     * Marks a seller's HELD payments as paid out. With no ids, every HELD payment of the seller is
     * included. Every listed payment must belong to the seller and still be HELD.
     */
    @Transactional
    public PayoutActionResult markPaidOut(Long providerId, Collection<Long> paymentIds, String reference, String adminEmail) {
        if (providerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT", "Choose a seller");
        }
        String ref = requireReference(reference);
        List<PaymentRecord> rows = paymentIds == null || paymentIds.isEmpty()
                ? paymentRecordRepository.findForSettlementForUpdate(SettlementStatus.HELD, providerId)
                : paymentRecordRepository.findAllByIdInForUpdate(paymentIds);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT", "There is nothing owed to this seller");
        }
        if (paymentIds != null && !paymentIds.isEmpty() && rows.size() != paymentIds.stream().distinct().count()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYOUT", "Some payments were not found");
        }
        for (PaymentRecord pr : rows) {
            if (!pr.getProvider().getId().equals(providerId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT", "A payment belongs to another seller");
            }
            if (pr.getSettlementStatus() != SettlementStatus.HELD) {
                throw new ApiException(HttpStatus.CONFLICT, "PAYOUT", "Payment " + pr.getId() + " is not awaiting payout");
            }
        }
        return settle(rows, SettlementStatus.PAID_OUT, ref, adminEmail, PaymentRecord::getSellerPayoutAmount);
    }

    /** Marks REFUND_DUE payments as REFUNDED (the buyer was refunded the full amount). */
    @Transactional
    public PayoutActionResult markRefunded(Collection<Long> paymentIds, String reference, String adminEmail) {
        if (paymentIds == null || paymentIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REFUND", "Choose the payments that were refunded");
        }
        String ref = requireReference(reference);
        List<PaymentRecord> rows = paymentRecordRepository.findAllByIdInForUpdate(paymentIds);
        if (rows.size() != paymentIds.stream().distinct().count()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REFUND", "Some payments were not found");
        }
        for (PaymentRecord pr : rows) {
            if (pr.getSettlementStatus() != SettlementStatus.REFUND_DUE) {
                throw new ApiException(HttpStatus.CONFLICT, "REFUND", "Payment " + pr.getId() + " is not awaiting a refund");
            }
        }
        return settle(rows, SettlementStatus.REFUNDED, ref, adminEmail, PaymentRecord::getAmount);
    }

    // ---- Provider (read only) ----

    @Transactional(readOnly = true)
    public ProviderPayoutsResponse forProvider(Long providerId) {
        List<PaymentRecord> rows = paymentRecordRepository.findSettledForProvider(
                providerId, SettlementStatus.NONE, PageRequest.of(0, 200));
        BigDecimal owed = BigDecimal.ZERO;
        BigDecimal paidOut = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        for (PaymentRecord pr : rows) {
            if (pr.getSettlementStatus() == SettlementStatus.HELD) {
                owed = owed.add(nz(pr.getSellerPayoutAmount()));
                fees = fees.add(nz(pr.getPlatformFee()));
            } else if (pr.getSettlementStatus() == SettlementStatus.PAID_OUT) {
                paidOut = paidOut.add(nz(pr.getSellerPayoutAmount()));
                fees = fees.add(nz(pr.getPlatformFee()));
            }
        }
        return new ProviderPayoutsResponse(
                currentFeePercent(), owed, paidOut, fees,
                rows.stream().map(pr -> toRow(pr, false)).toList());
    }

    private PayoutActionResult settle(
            List<PaymentRecord> rows,
            SettlementStatus to,
            String reference,
            String adminEmail,
            java.util.function.Function<PaymentRecord, BigDecimal> amountOf) {
        Instant now = Instant.now();
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentRecord pr : rows) {
            pr.setSettlementStatus(to);
            pr.setPaidOutAt(now);
            pr.setPaidOutBy(adminEmail);
            pr.setPayoutReference(reference);
            total = total.add(nz(amountOf.apply(pr)));
        }
        paymentRecordRepository.saveAll(rows);
        return new PayoutActionResult(rows.size(), total, to.name(), reference, now);
    }

    private static String requireReference(String reference) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_REFERENCE", "Enter the bank transfer reference");
        }
        if (ref.length() > 120) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYOUT_REFERENCE", "Reference is too long (120 characters at most)");
        }
        return ref;
    }

    static PaymentSettlementRow toRow(PaymentRecord pr, boolean includeAdminDetails) {
        String kind;
        Long refId;
        String code;
        String buyer;
        String status;
        if (pr.getOrder() != null) {
            kind = "ORDER";
            refId = pr.getOrder().getId();
            code = pr.getOrder().getVerificationCode();
            buyer = pr.getOrder().getGuestName();
            status = pr.getOrder().getStatus() == null ? null : pr.getOrder().getStatus().name();
        } else if (pr.getRentalBooking() != null) {
            kind = "RENTAL";
            refId = pr.getRentalBooking().getId();
            code = pr.getRentalBooking().getVerificationCode();
            buyer = pr.getRentalBooking().getGuestName();
            status = pr.getRentalBooking().getStatus() == null ? null : pr.getRentalBooking().getStatus().name();
        } else {
            kind = "OTHER";
            refId = null;
            code = null;
            buyer = null;
            status = null;
        }
        return new PaymentSettlementRow(
                pr.getId(),
                pr.getProvider().getId(),
                pr.getProvider().getName(),
                kind,
                refId,
                code,
                buyer,
                status,
                pr.getGateway() == null ? null : pr.getGateway().name(),
                pr.getAmount(),
                pr.getPlatformFeePercent(),
                pr.getPlatformFee(),
                pr.getSellerPayoutAmount(),
                pr.getSettlementStatus().name(),
                pr.getPaidAt() != null ? pr.getPaidAt() : pr.getCreatedAt(),
                pr.getPaidOutAt(),
                includeAdminDetails ? pr.getPaidOutBy() : null,
                pr.getPayoutReference());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
