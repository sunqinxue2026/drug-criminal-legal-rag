package com.gjq.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.dto.LegalDocumentImportResponse;
import com.gjq.entity.LegalDocument;
import com.gjq.vo.LegalDocumentDetailVO;
import com.gjq.vo.LegalDocumentIndexChunkVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface LegalDocumentService extends IService<LegalDocument> {

    Page<LegalDocument> getDocumentPage(int pageNum, int pageSize, Long categoryId, String keyword,
                                        String documentType, String caseType, String procedureDomain);

    LegalDocumentDetailVO getDocumentDetail(Long id);

    Map<String, Object> createDocument(LegalDocument document, boolean rebuildIndex);

    Map<String, Object> updateDocument(LegalDocument document, boolean rebuildIndex);

    Map<String, Object> deleteDocument(Long id, boolean rebuildIndex);

    LegalDocumentDetailVO previewFile(MultipartFile file, Long categoryId);

    LegalDocumentImportResponse importFiles(MultipartFile[] files, Long categoryId, boolean rebuildIndex);

    LegalDocumentImportResponse importOfficialDocuments(Long categoryId, boolean rebuildIndex);

    Map<String, Object> rebuildLegalIndex();

    Page<LegalDocumentIndexChunkVO> getIndexChunkPage(int pageNum, int pageSize);
}
