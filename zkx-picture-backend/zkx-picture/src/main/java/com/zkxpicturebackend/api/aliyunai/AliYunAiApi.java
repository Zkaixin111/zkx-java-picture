package com.zkxpicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.zkxpicturebackend.api.aliyunai.model.ImageRecognitionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 请求阿里云服务（使用 DashScope SDK）
 */
@Slf4j
@Component
public class AliYunAiApi {

    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    private static final String MODEL_NAME = "qwen-vl-plus";

    private static final String TAG_PROMPT = "你是校园图片素材平台的图片分析助手。请分析这张图片，返回一个JSON对象，包含两个字段：\n" +
            "1. \"tags\": 3个最相关的中文标签词数组，适合校园场景\n" +
            "2. \"category\": 从以下分类中选一个最匹配的：校园风景、创意设计、学习生活、校园建筑、人物写真、社团活动\n" +
            "只返回JSON对象，不要其他文字。例如：{\"tags\":[\"图书馆\",\"学习\"],\"category\":\"学习生活\"}";

    /**
     * 调用通义千问VL识别图片内容，返回智能标签和分类
     */
    public ImageRecognitionResult recognizeImage(String imageUrl) {
        ImageRecognitionResult result = new ImageRecognitionResult();
        result.setTags(new ArrayList<>());
        try {
            MultiModalConversation conv = new MultiModalConversation();

            MultiModalMessage message = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Collections.singletonMap("image", imageUrl),
                            Collections.singletonMap("text", TAG_PROMPT)
                    ))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .model(MODEL_NAME)
                    .apiKey(apiKey)
                    .message(message)
                    .build();

            MultiModalConversationResult convResult = conv.call(param);

            List<Map<String, Object>> contentList = convResult.getOutput()
                    .getChoices().get(0)
                    .getMessage().getContent();

            String content = "";
            for (Map<String, Object> item : contentList) {
                if (item.containsKey("text")) {
                    content = item.get("text").toString();
                    break;
                }
            }

            if (StrUtil.isBlank(content)) {
                log.warn("图像识别返回内容为空");
                return result;
            }

            return parseRecognitionResult(content);
        } catch (ApiException e) {
            log.error("DashScope API 调用失败: {}", e.getMessage());
        } catch (NoApiKeyException e) {
            log.error("未配置 API Key");
        } catch (Exception e) {
            log.error("图像智能识别失败", e);
        }
        return result;
    }

    /**
     * 从模型返回的文本中提取识别结果
     */
    private ImageRecognitionResult parseRecognitionResult(String content) {
        ImageRecognitionResult result = new ImageRecognitionResult();
        result.setTags(new ArrayList<>());
        try {
            String jsonStr = content.trim();
            if (jsonStr.startsWith("```")) {
                int start = jsonStr.indexOf('{');
                int end = jsonStr.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1);
                }
            }
            JSONObject obj = JSONUtil.parseObj(jsonStr);
            if (obj.containsKey("tags")) {
                List<String> tags = obj.getJSONArray("tags").toList(String.class);
                result.setTags(tags);
            }
            if (obj.containsKey("category")) {
                result.setCategory(obj.getStr("category"));
            }
        } catch (Exception e) {
            log.error("解析识别结果失败：{}", content, e);
        }
        return result;
    }
}
