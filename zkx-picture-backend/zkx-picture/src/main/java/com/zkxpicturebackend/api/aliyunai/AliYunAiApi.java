package com.zkxpicturebackend.api.aliyunai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zkxpicturebackend.api.aliyunai.model.ImageRecognitionResult;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求aliyun服务
 */
@Slf4j
@Component
public class AliYunAiApi {

    // 读取配置文件
    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    // OpenAI 兼容模式（用于通义千问VL视觉理解）
    private static final String CHAT_COMPLETION_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    // 智能标签 + 分类 prompt
    private static final String TAG_PROMPT = "你是校园图片素材平台的图片分析助手。请分析这张图片，返回一个JSON对象，包含两个字段：\n" +
            "1. \"tags\": 3个最相关的中文标签词数组，适合校园场景\n" +
            "2. \"category\": 从以下分类中选一个最匹配的：校园风景、创意设计、学习生活、校园建筑、人物写真、社团活动\n" +
            "只返回JSON对象，不要其他文字。例如：{\"tags\":[\"图书馆\",\"学习\"],\"category\":\"学习生活\"}";

    /**
     * 调用通义千问VL识别图片内容，返回智能标签和分类
     *
     * @param imageUrl 图片URL
     * @return 识别结果（标签列表 + 分类）
     */
    public ImageRecognitionResult recognizeImage(String imageUrl) {
        ImageRecognitionResult result = new ImageRecognitionResult();
        result.setTags(new ArrayList<>());
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(CHAT_COMPLETION_URL);
            httpPost.addHeader("Authorization", "Bearer " + apiKey);
            httpPost.addHeader("Content-Type", "application/json");

            // 构造请求体（OpenAI 兼容格式）
            String requestBody = JSONUtil.createObj()
                    .set("model", "qwen-vl-plus")
                    .set("messages", JSONUtil.createArray()
                            .set(JSONUtil.createObj()
                                    .set("role", "user")
                                    .set("content", JSONUtil.createArray()
                                            .set(JSONUtil.createObj()
                                                    .set("type", "image_url")
                                                    .set("image_url", JSONUtil.createObj()
                                                            .set("url", imageUrl)))
                                            .set(JSONUtil.createObj()
                                                    .set("type", "text")
                                                    .set("text", TAG_PROMPT)))))
                    .toString();

            StringEntity entity = new StringEntity(requestBody, "UTF-8");
            httpPost.setEntity(entity);
            CloseableHttpResponse response = httpClient.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

            if (response.getStatusLine().getStatusCode() != 200) {
                log.error("图像识别请求失败：{}", responseBody);
                return result;
            }

            // 解析响应，提取 content
            JSONObject json = JSONUtil.parseObj(responseBody);
            String content = json.getByPath("choices[0].message.content", String.class);
            if (StrUtil.isBlank(content)) {
                log.warn("图像识别返回内容为空");
                return result;
            }

            // 解析 JSON 对象（包含 tags 和 category）
            return parseRecognitionResult(content);
        } catch (Exception e) {
            log.error("图像智能识别失败", e);
            return result;
        }
    }

    /**
     * 从模型返回的文本中提取识别结果
     * 处理模型可能返回纯 JSON 对象、markdown 代码块包裹的 JSON 等情况
     */
    private ImageRecognitionResult parseRecognitionResult(String content) {
        ImageRecognitionResult result = new ImageRecognitionResult();
        result.setTags(new ArrayList<>());
        try {
            // 去除 markdown 代码块包裹
            String jsonStr = content.trim();
            if (jsonStr.startsWith("```")) {
                // 提取 ```json ... ``` 中间的内容
                int start = jsonStr.indexOf('{');
                int end = jsonStr.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    jsonStr = jsonStr.substring(start, end + 1);
                }
            }
            JSONObject obj = JSONUtil.parseObj(jsonStr);
            // 提取 tags
            if (obj.containsKey("tags")) {
                List<String> tags = obj.getJSONArray("tags").toList(String.class);
                result.setTags(tags);
            }
            // 提取 category
            if (obj.containsKey("category")) {
                result.setCategory(obj.getStr("category"));
            }
        } catch (Exception e) {
            log.error("解析识别结果失败：{}", content, e);
        }
        return result;
    }
}

