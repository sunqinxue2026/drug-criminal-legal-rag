package com.gjq.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gjq.common.Result;
import com.gjq.dto.LegalDocumentImportResponse;
import com.gjq.entity.LegalDocument;
import com.gjq.service.LegalDocumentService;
import com.gjq.service.support.RequestAuthorizationService;
import com.gjq.vo.LegalDocumentDetailVO;
import com.gjq.vo.LegalDocumentIndexChunkVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/legal-document")
public class LegalDocumentController {

    private final LegalDocumentService legalDocumentService;
    private final RequestAuthorizationService requestAuthorizationService;

    public LegalDocumentController(LegalDocumentService legalDocumentService,
                                   RequestAuthorizationService requestAuthorizationService) {
        this.legalDocumentService = legalDocumentService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/page")
    public Result<Page<LegalDocument>> getDocumentPage(@RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String documentType,
                                                       @RequestParam(required = false) String caseType,
                                                       @RequestParam(required = false) String procedureDomain,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(legalDocumentService.getDocumentPage(
                pageNum, pageSize, categoryId, keyword, documentType, caseType, procedureDomain
        ));
    }

    @GetMapping("/{id}")
    public Result<LegalDocumentDetailVO> getDocumentDetail(@PathVariable Long id, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        LegalDocumentDetailVO detailVO = legalDocumentService.getDocumentDetail(id);
        if (detailVO == null) {
            return Result.error("文书不存在");
        }
        return Result.success(detailVO);
    }

    @PostMapping
    public Result<Map<String, Object>> createDocument(@RequestBody LegalDocument document,
                                                      @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                      HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalDocumentService.createDocument(document, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "涓撻鏂囦功鍒涘缓澶辫触")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "涓撻鏂囦功鍒涘缓鎴愬姛")), response);
    }

    @PutMapping
    public Result<Map<String, Object>> updateDocument(@RequestBody LegalDocument document,
                                                      @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                      HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalDocumentService.updateDocument(document, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "涓撻鏂囦功鏇存柊澶辫触")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "涓撻鏂囦功鏇存柊鎴愬姛")), response);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> deleteDocument(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                      HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        Map<String, Object> response = legalDocumentService.deleteDocument(id, rebuildIndex);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            return Result.error(String.valueOf(response.getOrDefault("message", "专题文书删除失败")));
        }
        return Result.success(String.valueOf(response.getOrDefault("message", "专题文书已删除")), response);
    }

    @PostMapping("/preview/file")
    public Result<LegalDocumentDetailVO> previewFile(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(required = false) Long categoryId,
                                                     HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        LegalDocumentDetailVO detailVO = legalDocumentService.previewFile(file, categoryId);
        if (detailVO == null) {
            return Result.error("文书预览失败");
        }
        return Result.success("文书预览成功", detailVO);
    }

    @PostMapping("/import/file")
    public Result<LegalDocumentImportResponse> importSingleFile(@RequestParam("file") MultipartFile file,
                                                                @RequestParam(required = false) Long categoryId,
                                                                @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                                HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        LegalDocumentImportResponse response = legalDocumentService.importFiles(
                new MultipartFile[]{file}, categoryId, rebuildIndex
        );
        return Result.success("文书导入完成", response);
    }

    @PostMapping("/import/files")
    public Result<LegalDocumentImportResponse> importBatchFiles(@RequestParam("files") MultipartFile[] files,
                                                                @RequestParam(required = false) Long categoryId,
                                                                @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                                HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("批量导入完成", legalDocumentService.importFiles(files, categoryId, rebuildIndex));
    }

    @PostMapping("/reindex")
    public Result<Map<String, Object>> rebuildIndex(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("法律 RAG 索引刷新完成", legalDocumentService.rebuildLegalIndex());
    }

    @PostMapping("/import/official")
    public Result<LegalDocumentImportResponse> importOfficialDocuments(@RequestParam(required = false) Long categoryId,
                                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("官方专题文书导入完成", legalDocumentService.importOfficialDocuments(categoryId, rebuildIndex));
    }

    @GetMapping("/index/chunks")
    public Result<Page<LegalDocumentIndexChunkVO>> getIndexChunks(@RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "200") int pageSize,
                                                                  HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success(legalDocumentService.getIndexChunkPage(pageNum, pageSize));
    }
}
