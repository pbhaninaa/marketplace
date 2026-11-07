package com.agrimarket.util.validation;

import com.agrimarket.util.InputValidator;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Phone number: country code plus 9 digits; local {@code 0XX...} shapes are read as South African
 * (see {@link InputValidator#normalizePhone}). Null/blank passes; combine with {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = ValidPhone.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {
    String message() default InputValidator.PHONE_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPhone, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || InputValidator.isValidPhone(value);
        }
    }
}
