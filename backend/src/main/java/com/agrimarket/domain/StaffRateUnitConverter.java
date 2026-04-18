package com.agrimarket.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Backwards-compatible converter for legacy DB values.
 * Older rows may contain payment methods (EFT/CASH/CARD) in the staff rate unit column.
 */
@Converter(autoApply = false)
public class StaffRateUnitConverter implements AttributeConverter<StaffRateUnit, String> {

    @Override
    public String convertToDatabaseColumn(StaffRateUnit attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public StaffRateUnit convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        String v = dbData.trim().toUpperCase();
        if (v.isEmpty()) return null;
        // tolerate legacy values
        if (v.equals("EFT") || v.equals("CASH") || v.equals("CARD")) {
            return StaffRateUnit.HOURLY;
        }
        try {
            return StaffRateUnit.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }
}

