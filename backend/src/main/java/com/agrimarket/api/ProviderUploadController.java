package com.agrimarket.api;

import com.agrimarket.api.dto.UploadImageResponse;
import com.agrimarket.api.error.ApiException;
import com.agrimarket.repo.ProviderRepository;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderListingImageService;
import com.agrimarket.service.TenantAccess;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/provider/me/uploads")
@RequiredArgsConstructor
public class ProviderUploadController {

    private final ProviderRepository providerRepository;
    private final ProviderListingImageService providerListingImageService;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadImageResponse uploadImages(
            @AuthenticationPrincipal MarketUserPrincipal user, @RequestParam("files") List<MultipartFile> files) {
        TenantAccess.requireProviderUser(user);
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD", "No files uploaded");
        }
        if (!providerRepository.existsById(user.getProviderId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER", "Provider not found");
        }
        List<String> urls = providerListingImageService.saveImages(user.getProviderId(), files);
        if (urls.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD", "No valid images uploaded");
        }
        return new UploadImageResponse(urls);
    }
}
