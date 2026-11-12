package com.gjq.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gjq.common.Result;
import com.gjq.dto.ItemImportResponse;
import com.gjq.dto.ItemPreviewResponse;
import com.gjq.dto.OfficialCaseSyncResponse;
import com.gjq.entity.Item;
import com.gjq.entity.ItemAsset;
import com.gjq.service.ItemImportService;
import com.gjq.service.ItemService;
import com.gjq.service.OfficialCaseSyncService;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/item")
public class ItemController {

    private final ItemService itemService;
    private final ItemImportService itemImportService;
    private final OfficialCaseSyncService officialCaseSyncService;
    private final RequestAuthorizationService requestAuthorizationService;

    public ItemController(ItemService itemService,
                          ItemImportService itemImportService,
                          OfficialCaseSyncService officialCaseSyncService,
                          RequestAuthorizationService requestAuthorizationService) {
        this.itemService = itemService;
        this.itemImportService = itemImportService;
        this.officialCaseSyncService = officialCaseSyncService;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @GetMapping("/page")
    public Result<Page<Item>> getItemPage(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String tags) {
        return Result.success(itemService.getItemPage(pageNum, pageSize, categoryId, keyword, tags));
    }

    @GetMapping("/{id}")
    public Result<Item> getItemDetail(@PathVariable Long id,
                                      @RequestParam(required = false) Long userId,
                                      HttpServletRequest request) {
        if (userId != null) {
            requestAuthorizationService.requireUserAccess(request, userId);
        }
        Item item = itemService.getItemDetail(id, userId);
        if (item == null) {
            return Result.error("专题案例不存在");
        }
        return Result.success(item);
    }

    @PostMapping
    public Result<Item> createItem(@RequestBody Item item, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("专题案例创建成功", itemService.createItem(item));
    }

    @PutMapping
    public Result<Item> updateItem(@RequestBody Item item, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("专题案例更新成功", itemService.updateItem(item));
    }

    @DeleteMapping("/{id}")
    public Result<String> deleteItem(@PathVariable Long id, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        itemService.removeById(id);
        return Result.success("专题案例删除成功");
    }

    @PostMapping("/preview/file")
    public Result<ItemPreviewResponse> previewFile(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) Long categoryId,
                                                   HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        ItemPreviewResponse response = itemImportService.previewFile(file, categoryId);
        if (response == null) {
            return Result.error("专题案例预览失败");
        }
        return Result.success("专题案例预览成功", response);
    }

    @PostMapping("/import/file")
    public Result<ItemImportResponse> importSingleFile(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        ItemImportResponse response = itemImportService.importFiles(new MultipartFile[]{file}, categoryId, rebuildIndex);
        return Result.success("专题案例导入完成", response);
    }

    @PostMapping("/import/files")
    public Result<ItemImportResponse> importBatchFiles(@RequestParam("files") MultipartFile[] files,
                                                       @RequestParam(required = false) Long categoryId,
                                                       @RequestParam(defaultValue = "true") boolean rebuildIndex,
                                                       HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("专题案例批量导入完成", itemImportService.importFiles(files, categoryId, rebuildIndex));
    }

    @PostMapping("/reindex")
    public Result<Map<String, Object>> rebuildIndex(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("专题案例索引刷新完成", itemImportService.rebuildLegalIndex());
    }

    @PostMapping("/sync/official")
    public Result<OfficialCaseSyncResponse> syncOfficialCases(HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        OfficialCaseSyncResponse response = officialCaseSyncService.syncOfficialCases();
        return Result.success(response.getMessage(), response);
    }

    @GetMapping("/{id}/assets")
    public Result<List<ItemAsset>> getItemAssets(@PathVariable Long id) {
        return Result.success(itemService.getItemAssets(id));
    }

    @PostMapping("/{id}/assets")
    public Result<ItemAsset> addItemAsset(@PathVariable Long id,
                                          @RequestBody ItemAsset asset,
                                          HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        return Result.success("案例附件绑定成功", itemService.addItemAsset(id, asset));
    }

    @DeleteMapping("/assets/{assetId}")
    public Result<String> deleteItemAsset(@PathVariable Long assetId, HttpServletRequest request) {
        requestAuthorizationService.requireAdmin(request);
        itemService.deleteItemAsset(assetId);
        return Result.success("案例附件删除成功");
    }
}
