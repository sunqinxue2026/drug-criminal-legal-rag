package com.gjq.controller;

import com.gjq.client.FileClient;
import com.gjq.common.Result;
import com.gjq.service.support.RequestAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileClient fileClient;
    private final RequestAuthorizationService requestAuthorizationService;

    public FileController(FileClient fileClient, RequestAuthorizationService requestAuthorizationService) {
        this.fileClient = fileClient;
        this.requestAuthorizationService = requestAuthorizationService;
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "bucket", defaultValue = "default") String bucket,
                                                  HttpServletRequest request) {
        try {
            requestAuthorizationService.requireCurrentUser(request);
            Map<String, Object> result = fileClient.uploadFile(file, bucket);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("鏂囦欢涓婁紶澶辫触: " + e.getMessage());
        }
    }

    @GetMapping("/url")
    public Result<String> getFileUrl(@RequestParam String bucket, @RequestParam String objectKey) {
        String url = fileClient.getFileUrl(bucket, objectKey);
        return Result.success("操作成功", url);
    }
}
