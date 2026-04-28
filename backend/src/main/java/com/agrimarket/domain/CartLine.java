package com.agrimarket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
public class CartLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * CartLine is used in two contexts:
     * - as a line in a guest cart session (cartSession != null, order == null)
     * - as a persisted line item in an Order (order != null, cartSession == null)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_session_id")
    private CartSession cartSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "rental_start")
    private Instant rentalStart;

    @Column(name = "rental_end")
    private Instant rentalEnd;
}
