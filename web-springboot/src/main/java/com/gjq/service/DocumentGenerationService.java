package com.gjq.service;

import com.gjq.dto.DocumentGenerateDTO;

import java.util.Map;

public interface DocumentGenerationService {
    Map<String, Object> generateDocument(DocumentGenerateDTO request);
}
