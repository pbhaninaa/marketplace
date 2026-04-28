package com.agrimarket.api;

import com.agrimarket.api.dto.UploadImageResponse;
import com.agrimarket.security.MarketUserPrincipal;
import com.agrimarket.service.ProviderListingImageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

    private final ProviderListingImageService providerListingImageService;

    @PostMapping("/images")
    public UploadImageResponse uploadImages(
            @AuthenticationPrincipal MarketUserPrincipal actor,
            @RequestParam("files") List<MultipartFile> files) {
        List<String> urls = providerListingImageService.saveImages(actor.getProviderId(), files);
        return new UploadImageResponse(urls);
    }
}

