package com.agrimarket.service;

import com.agrimarket.repo.CategoryRepository;
import com.agrimarket.repo.ProviderRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlugService {

    private static final Pattern NON_LATIN = Pattern.compile("[^a-z0-9-]");

    private final ProviderRepository providerRepository;
    private final CategoryRepository categoryRepository;

    public String uniqueProviderSlug(String businessName) {
        String base = slugify(businessName);
        String candidate = base;
        int i = 1;
        while (providerRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    /** URL-safe slug unique among {@code categories.slug}. */
    public String uniqueCategorySlug(String displayName) {
        String base = slugify(displayName);
        String candidate = base;
        int i = 1;
        while (categoryRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", "and")
                .replace(' ', '-');
        normalized = NON_LATIN.matcher(normalized).replaceAll("");
        normalized = normalized.replaceAll("-{2,}", "-");
        if (normalized.isBlank()) {
            return "provider";
        }
        return normalized;
    }
}
