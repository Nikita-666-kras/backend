package com.blog.platform.parts.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.parts.api.dto.ImportDtos.ImportApplyRequest;
import com.blog.platform.parts.api.dto.ImportDtos.ImportApplyResponse;
import com.blog.platform.parts.api.dto.ImportDtos.ImportPreviewResponse;
import com.blog.platform.parts.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/parts/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @GetMapping("/template.csv")
    public ResponseEntity<byte[]> template() {
        byte[] body = importService.csvTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"parts-import-template.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> preview(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.of(importService.preview(file)));
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportApplyResponse>> apply(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "options", required = false) ImportApplyRequest options
    ) {
        ImportApplyRequest request = options == null
                ? new ImportApplyRequest(null, true, true, true, "DRAFT")
                : options;
        return ResponseEntity.ok(ApiResponse.of(importService.apply(file, request)));
    }
}
