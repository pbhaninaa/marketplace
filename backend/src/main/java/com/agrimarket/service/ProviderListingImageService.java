package com.agrimarket.service;

import com.agrimarket.api.error.ApiException;
import com.agrimarket.domain.ListingImage;
import com.agrimarket.domain.Provider;
import com.agrimarket.repo.ListingImageRepository;
import com.agrimarket.repo.ProviderRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProviderListingImageService {

    private final ProviderRepository providerRepository;
    private final ListingImageRepository listingImageRepository;

    /**
     * Persists image bytes and returns public URL paths ({@code /api/public/images/{id}}) in order.
     */
    @Transactional
    public List<String> saveImages(Long providerId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        Provider provider = providerRepository
                .findById(providerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found"));
        List<String> urls = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f == null || f.isEmpty()) {
                continue;
            }
            String ct = f.getContentType();
            if (ct == null || !ct.startsWith("image/")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD", "Only image uploads are allowed");
            }
            try {
                ListingImage img = new ListingImage();
                img.setProvider(provider);
                img.setOriginalFilename(f.getOriginalFilename());
                img.setContentType(ct);
                img.setData(f.getBytes());
                ListingImage saved = listingImageRepository.save(img);
                urls.add("/api/public/images/" + saved.getId());
            } catch (IOException e) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD", "Failed to save upload");
            }
        }
        return urls;
    }
}
