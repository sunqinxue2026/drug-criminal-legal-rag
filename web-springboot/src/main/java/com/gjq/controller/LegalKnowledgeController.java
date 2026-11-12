package com.gjq.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gjq.common.Result;
import com.gjq.dto.LegalKnowledgeImportResponse;
import com.gjq.dto.LegalKnowledgeImportResult;
import com.gjq.dto.LegalKnowledgeRecordRequest;
import com.gjq.entity.LegalKnowledge;
import com.gjq.service.LegalKnowledgeService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.vo.LegalKnowledgeDetailVO;
import com.gjq.vo.LegalKnowledgeIndexChunkVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/legal-knowledge")
public class LegalKnowledgeController {

    private final LegalKnowledgeService legalKnowledgeService;
    private final RequestAuthorizationService requestAuthorizationService;

    public LegalKnowledgeController(LegalKnowledgeService legalKnowledgeService,
                                    RequestAuthorizationService requestAuthorizationService) {
        this.legalKnowledgeService = legalKnowledgeService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/page")
    public Result<Page<LegalKnowledge>> getKnowledgePage(@RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "10") int pageSize,
                                                         @RequestParam(required = false) Long categoryId,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String knowledgeType,
                                                         @RequestParam(required = false) String businessDomain,
                                                         HttpServletRequest request) {
        return Result.success(legalKnowledgeService.getKnowledgePage(
                pageNum, pageSize, categoryId, keyword, knowledgeType, businessDomain
        ));
    }

    @GetMapping("/{id}")
    public Result<LegalKnowledgeDetailVO> getKnowledgeDetail(@PathVariable Long id, HttpServletRequest request) {
        LegalKnowledgeDetailVO detailVO = legalKnowledgeService.getKnowledgeDetail(id);
        if (detailVO == null) {
            return Result.error("规范知识不存在");
        }
        return Result.success(detailVO);
    }

    @PostMapping
    public Result<Map<String, Object>> createKnowledge(@RequestBody LegalKnowledge knowledge,
                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalKnowledgeService.createKnowledge(knowledge, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "瑙勮寖鐭ヨ瘑鍒涘缓澶辫触")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "瑙勮寖鐭ヨ瘑鍒涘缓鎴愬姛")), response);
    }

    @PutMapping
    public Result<Map<String, Object>> updateKnowledge(@RequestBody LegalKnowledge knowledge,
                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalKnowledgeService.updateKnowledge(knowledge, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "瑙勮寖鐭ヨ瘑鏇存柊澶辫触")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "瑙勮寖鐭ヨ瘑鏇存柊鎴愬姛")), response);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> deleteKnowledge(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalKnowledgeService.deleteKnowledge(id, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "规范知识删除失败")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "规范知识已删除")), response);
    }

    @PostMapping("/import/file")
    public Result<LegalKnowledgeImportResponse> importJsonFile(@RequestParam("file") MultipartFile file,
                                                               @RequestParam(required = false) Long categoryId,
                                                               @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                               HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("规范知识导入完成", legalKnowledgeService.importJsonFile(file, categoryId, rebuildIndex));
    }

    @PostMapping("/import/official")
    public Result<LegalKnowledgeImportResponse> importOfficialKnowledge(@RequestParam(required = false) Long categoryId,
                                                                        @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                                        HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("官方知识内容同步完成", legalKnowledgeService.importOfficialKnowledge(categoryId, rebuildIndex));
    }

    @PostMapping("/preview/record")
    public Result<LegalKnowledgeDetailVO> previewRecord(@RequestBody LegalKnowledgeRecordRequest requestBody,
                                                        HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        LegalKnowledgeDetailVO detailVO = legalKnowledgeService.previewRecord(
                requestBody.getRecord(),
                requestBody.getSourceFileName(),
                requestBody.getCategoryId()
        );
        if (detailVO == null) {
            return Result.error("规范知识预览失败");
        }
        return Result.success("规范知识预览成功", detailVO);
    }

    @PostMapping("/import/record")
    public Result<LegalKnowledgeImportResult> importRecord(@RequestBody LegalKnowledgeRecordRequest requestBody,
                                                           HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        LegalKnowledgeImportResult result = legalKnowledgeService.importRecord(
                requestBody.getRecord(),
                requestBody.getSourceFileName(),
                requestBody.getCategoryId()
        );
        return Result.success("规范记录导入完成", result);
    }

    @PostMapping("/reindex")
    public Result<Map<String, Object>> rebuildIndex(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("规范知识索引刷新完成", legalKnowledgeService.rebuildLegalIndex());
    }

    @GetMapping("/index/chunks")
    public Result<Page<LegalKnowledgeIndexChunkVO>> getIndexChunks(@RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "200") int pageSize,
                                                                   HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(legalKnowledgeService.getIndexChunkPage(pageNum, pageSize));
    }
}
