package com.gjq.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gjq.common.Result;
import com.gjq.entity.Category;
import com.gjq.entity.Item;
import com.gjq.service.CategoryService;
import com.gjq.service.ItemService;
import com.gjq.service.LegalDocumentService;
import com.gjq.service.LegalKnowledgeService;
import com.gjq.vo.LegalDocumentIndexChunkVO;
import com.gjq.vo.LegalKnowledgeIndexChunkVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
public class InternalDataController {

    private static final int INTERNAL_PAGE_SIZE = 500;

    private final ItemService itemService;
    private final CategoryService categoryService;
    private final LegalDocumentService legalDocumentService;
    private final LegalKnowledgeService legalKnowledgeService;

    public InternalDataController(ItemService itemService,
                                  CategoryService categoryService,
                                  LegalDocumentService legalDocumentService,
                                  LegalKnowledgeService legalKnowledgeService) {
        this.itemService = itemService;
        this.categoryService = categoryService;
        this.legalDocumentService = legalDocumentService;
        this.legalKnowledgeService = legalKnowledgeService;
    }

    @GetMapping("/items")
    public Result<List<Map<String, Object>>> getAllItems() {
        Map<Long, String> categoryNames = categoryService.getAllCategories().stream()
                .filter(category -> Objects.nonNull(category.getId()))
                .collect(Collectors.toMap(Category::getId, Category::getName, (left, right) -> left, LinkedHashMap::new));

        List<Map<String, Object>> items = itemService.list(new LambdaQueryWrapper<Item>()
                        .eq(Item::getStatus, 1)
                        .eq(Item::getDeleted, 0)
                        .orderByDesc(Item::getCreateTime))
                .stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", item.getId());
                    row.put("title", item.getTitle());
                    row.put("content", item.getContent());
                    row.put("summary", item.getSummary());
                    row.put("categoryId", item.getCategoryId());
                    row.put("categoryName", categoryNames.get(item.getCategoryId()));
                    row.put("tags", item.getTags());
                    return row;
                })
                .toList();
        return Result.success(items);
    }

    @GetMapping("/legal-document/chunks")
    public Result<List<LegalDocumentIndexChunkVO>> getAllLegalDocumentChunks() {
        return Result.success(collectPages(pageNum -> legalDocumentService.getIndexChunkPage(pageNum, INTERNAL_PAGE_SIZE)));
    }

    @GetMapping("/legal-knowledge/chunks")
    public Result<List<LegalKnowledgeIndexChunkVO>> getAllLegalKnowledgeChunks() {
        return Result.success(collectPages(pageNum -> legalKnowledgeService.getIndexChunkPage(pageNum, INTERNAL_PAGE_SIZE)));
    }

    private <T> List<T> collectPages(Function<Integer, Page<T>> fetchPage) {
        List<T> records = new ArrayList<>();
        int pageNum = 1;
        while (true) {
            Page<T> page = fetchPage.apply(pageNum);
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }
            records.addAll(page.getRecords());
            if (pageNum >= page.getPages()) {
                break;
            }
            pageNum++;
        }
        return records;
    }
}
