package com.zkxpicturebackend.api.aliyunai.model;

import lombok.Data;
import java.util.List;

/**
 * AI 图像识别结果（标签 + 分类）
 */
@Data
public class ImageRecognitionResult {
    /**
     * 标签列表
     */
    private List<String> tags;
    /**
     * 分类
     */
    private String category;
}
