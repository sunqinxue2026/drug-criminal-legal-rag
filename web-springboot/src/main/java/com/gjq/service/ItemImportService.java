package com.gjq.service;

import com.gjq.dto.ItemImportResponse;
import com.gjq.dto.ItemPreviewResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ItemImportService {
    ItemPreviewResponse previewFile(MultipartFile file, Long categoryId);

    ItemImportResponse importFiles(MultipartFile[] files, Long categoryId, boolean rebuildIndex);

    Map<String, Object> rebuildLegalIndex();
}
