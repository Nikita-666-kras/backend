package com.blog.platform.admin.service;

import com.blog.platform.admin.api.dto.AdminDtos.BulkRequest;
import com.blog.platform.admin.api.dto.AdminDtos.BulkResult;
import com.blog.platform.admin.client.PartsServiceClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPartsBulkService {

    private final PartsServiceClient partsServiceClient;

    public BulkResult bulk(BulkRequest request) {
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (UUID id : request.ids()) {
            try {
                switch (action) {
                    case "PUBLISH" -> partsServiceClient.updatePartStatus(id, "PUBLISHED");
                    case "ARCHIVE" -> partsServiceClient.updatePartStatus(id, "ARCHIVED");
                    case "DELETE" -> partsServiceClient.deletePart(id);
                    default -> throw new IllegalArgumentException("Неизвестное действие: " + action);
                }
                success++;
            } catch (Exception ex) {
                errors.add(id + ": " + ex.getMessage());
            }
        }
        return new BulkResult(success, errors.size(), errors);
    }
}
