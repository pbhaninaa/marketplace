package com.agrimarket.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMaintenanceService {

    @PersistenceContext
    private EntityManager em;

    /**
     * DANGEROUS: wipes almost all rows and keeps only the specified platform admin user.
     * Intended for local/dev resets.
     */
    @Transactional
    public Map<String, Object> cleanDbKeepAdmin(long keepAdminUserId) {
        Map<String, Object> out = new LinkedHashMap<>();

        // Delete children first to satisfy foreign keys.
        // These deletes intentionally remove *all* rows (including anything related to the admin),
        // except for the final `users` delete which keeps the admin account.
        out.put("provider_staff_permissions", del("DELETE FROM provider_staff_permissions"));
        out.put("staff_payroll_entries", del("DELETE FROM staff_payroll_entries"));
        out.put("password_reset_tokens", del("DELETE FROM password_reset_tokens"));
        out.put("client_otp_challenges", del("DELETE FROM client_otp_challenges"));
        out.put("support_tickets", del("DELETE FROM support_tickets"));
        out.put("payment_records", del("DELETE FROM payment_records"));
        out.put("rental_bookings", del("DELETE FROM rental_bookings"));
        out.put("order_lines", del("DELETE FROM order_lines"));
        out.put("purchase_orders", del("DELETE FROM purchase_orders"));
        out.put("cart_lines", del("DELETE FROM cart_lines"));
        out.put("cart_sessions", del("DELETE FROM cart_sessions"));
        out.put("listing_images", del("DELETE FROM listing_images"));
        out.put("listings", del("DELETE FROM listings"));
        out.put("subscriptions", del("DELETE FROM subscriptions"));
        out.put("provider_accepted_payment_methods", del("DELETE FROM provider_accepted_payment_methods"));
        out.put("providers", del("DELETE FROM providers"));
        out.put("categories", del("DELETE FROM categories"));

        // Finally keep only this admin user.
        out.put("users", del("DELETE FROM users WHERE id <> ?1", keepAdminUserId));

        return out;
    }

    private int del(String sql) {
        return em.createNativeQuery(sql).executeUpdate();
    }

    private int del(String sql, long p1) {
        return em.createNativeQuery(sql).setParameter(1, p1).executeUpdate();
    }
}

