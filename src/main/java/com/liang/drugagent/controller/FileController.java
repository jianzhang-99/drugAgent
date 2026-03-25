package com.liang.drugagent.controller;

import com.liang.drugagent.shared.domain.response.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件控制器。
 *
 * <p>提供文件上传接口：
 * <ul>
 *   <li>POST /api/files/upload - 多文件上传</li>
 * </ul>
 *
 * @author liangjiajian
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "文件上传接口")
@CrossOrigin(origins = "*")
public class FileController {

    /**
     * 上传多个文件。
     *
     * <p>支持多文件同时上传，返回每个文件的ID列表。</p>
     *
     * @param files 上传的文件列表
     * @return 上传结果，包含文件ID列表
     */
    @Operation(summary = "文件上传", description = "支持多文件上传，返回文件ID列表")
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFiles(
            @Parameter(description = "上传的文件列表", required = true) @RequestParam("files") MultipartFile[] files) {
        log.info("File upload request: fileCount={}", files == null ? 0 : files.length);

        if (files == null || files.length == 0) {
            return Result.error("请至少上传一个文件");
        }

        List<Map<String, Object>> fileInfos = new ArrayList<>();
        List<String> fileIds = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileId = UUID.randomUUID().toString();
            fileIds.add(fileId);

            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("fileId", fileId);
            fileInfo.put("filename", file.getOriginalFilename());
            fileInfo.put("size", file.getSize());
            fileInfo.put("contentType", file.getContentType());
            fileInfos.add(fileInfo);

            log.info("File uploaded: fileId={}, filename={}, size={}",
                    fileId, file.getOriginalFilename(), file.getSize());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileIds", fileIds);
        result.put("files", fileInfos);
        result.put("totalCount", files.length);

        return Result.success(result);
    }
}
