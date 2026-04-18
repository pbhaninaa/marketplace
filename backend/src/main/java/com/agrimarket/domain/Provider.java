package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "providers")
@Getter
@Setter
@NoArgsConstructor
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String location;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "bank_account_name", length = 200)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_branch_code", length = 20)
    private String bankBranchCode;

    @Column(name = "bank_reference", length = 140)
    private String bankReference;

    @ElementCollection
    @CollectionTable(name = "provider_accepted_payment_methods", joinColumns = @JoinColumn(name = "provider_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private Set<PaymentMethod> acceptedPaymentMethods;

    @Enumerated(EnumType.STRING)
    @Column(name = "subtype", nullable = false)
    private ProviderSubtype subtype = ProviderSubtype.RESELLER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status = ProviderStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Provider(String name, String slug, String description, String location) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.location = location;
    }
}
