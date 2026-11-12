package com.gjq.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gjq.dto.LegalKnowledgeImportResponse;
import com.gjq.dto.LegalKnowledgeImportResult;
import com.gjq.entity.LegalKnowledge;
import com.gjq.vo.LegalKnowledgeDetailVO;
import com.gjq.vo.LegalKnowledgeIndexChunkVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface LegalKnowledgeService extends IService<LegalKnowledge> {

    Page<LegalKnowledge> getKnowledgePage(int pageNum, int pageSize, Long categoryId, String keyword,
                                          String knowledgeType, String businessDomain);

    LegalKnowledgeDetailVO getKnowledgeDetail(Long id);

    Map<String, Object> createKnowledge(LegalKnowledge knowledge, boolean rebuildIndex);

    Map<String, Object> updateKnowledge(LegalKnowledge knowledge, boolean rebuildIndex);

    Map<String, Object> deleteKnowledge(Long id, boolean rebuildIndex);

    LegalKnowledgeImportResponse importJsonFile(MultipartFile file, Long categoryId, boolean rebuildIndex);

    LegalKnowledgeImportResponse importOfficialKnowledge(Long categoryId, boolean rebuildIndex);

    LegalKnowledgeDetailVO previewRecord(Map<String, Object> record, String sourceFileName, Long categoryId);

    LegalKnowledgeImportResult importRecord(Map<String, Object> record, String sourceFileName, Long categoryId);

    Map<String, Object> rebuildLegalIndex();

    Page<LegalKnowledgeIndexChunkVO> getIndexChunkPage(int pageNum, int pageSize);
}
