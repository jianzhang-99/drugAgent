package com.liang.drugagent.shared.ocr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OCR 文档解析请求。
 *
 * @author liangjiajian
 * @since 2026-04-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrRequest {

    /**
     * 图片或文档的 URL 或 Base64 编码
     */
    private String imageUrl;

    /**
     * 图片的 Base64 编码数据
     */
    private String imageBase64;

    /**
     * 文件类型：pdf, image, png, jpg, jpeg, bmp, tiff
     */
    private String fileType;

    /**
     * 文档语言：zh, en, auto（自动检测）
     */
    @Builder.Default
    private String language = "auto";

    /**
     * 是否返回表格结构
     */
    @Builder.Default
    private Boolean returnTable = true;

    /**
     * 是否返回段落结构
     */
    @Builder.Default
    private Boolean returnParagraph = true;

    /**
     * 图片列表（用于多页文档）
     */
    private List<String> images;

    /**
     * 额外参数
     */
    private String extraParams;
}
