package com.zkxpicturebackend.manager.rabbitmq;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.zkxpicturebackend.api.aliyunai.AliYunAiApi;
import com.zkxpicturebackend.api.aliyunai.model.ImageRecognitionResult;
import com.zkxpicturebackend.model.entity.Picture;
import com.zkxpicturebackend.service.PictureService;
import com.zkxpicturebackend.service.impl.PictureServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AiTagConsumer {
    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 正常队列消费者
     */
    @RabbitListener(queues = AiTagRabbitMQConfig.AI_TAG_QUEUE)
    public void handleAiTagMessage(AiTagMessage message, Channel channel, Message rawMessage) {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        Long pictureId = message.getPictureId();
        try {
            // ===== 幂等校验：已经有标签了就不重复处理 =====
            String redisKey = "ai:tag:processed:" + pictureId;
            Boolean firstTime = stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
            if (firstTime == null || !firstTime) {
                log.info("消息已处理过，跳过, pictureId={}", pictureId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("开始生成 AI 标签, pictureId={}, url={}", pictureId, message.getImageUrl());

            // 1. 查图片是否存在
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                log.warn("图片不存在，跳过, pictureId={}", pictureId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 如果已有标签，不重复生成
            if (StrUtil.isNotBlank(picture.getTags())) {
                log.info("图片已有标签，跳过, pictureId={}", pictureId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 调 AI 接口（直接使用消息中的 URL，避免重复取 picture.getUrl()）
            ImageRecognitionResult recognition = aliYunAiApi.recognizeImage(message.getImageUrl());

            // 4. 更新数据库
            Picture update = new Picture();
            update.setId(pictureId);
            boolean hasUpdate = false;

            if (CollUtil.isNotEmpty(recognition.getTags())) {
                List<String> tags = recognition.getTags();
                if (tags.size() > 3) {
                    tags = tags.subList(0, 3);
                }
                update.setTags(JSONUtil.toJsonStr(tags));
                hasUpdate = true;
            }
            if (StrUtil.isNotBlank(recognition.getCategory())) {
                update.setCategory(recognition.getCategory());
                hasUpdate = true;
            }

            if (hasUpdate) {
                pictureService.updateById(update);
                // 清理热门标签缓存
                stringRedisTemplate.delete(PictureServiceImpl.HOT_TAGS_REDIS_KEY);
                log.info("AI 标签生成成功, pictureId={}", pictureId);
            }

            // 5. ack 确认
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("AI 标签生成失败, pictureId={}", pictureId, e);

            try {
                // 判断重试次数
                Object xDeath = rawMessage.getMessageProperties().getXDeathHeader();
                int retryCount = (xDeath == null) ? 0 : 1;

                if (retryCount >= 3) {
                    // 超过 3 次 → 进死信队列
                    log.error("重试 {} 次仍失败，进入死信队列, pictureId={}", retryCount, pictureId);
                    channel.basicNack(deliveryTag, false, false);
                } else {
                    // 重新入队重试
                    log.warn("第 {} 次重试, pictureId={}", retryCount + 1, pictureId);
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception ex) {
                log.error("ack/nack 异常", ex);
            }
        }
    }

    /**
     * 死信队列消费者
     */
    @RabbitListener(queues = AiTagRabbitMQConfig.AI_TAG_DLQ_QUEUE)
    public void handleDeadLetter(AiTagMessage message, Channel channel, Message rawMessage) {
        long deliveryTag = rawMessage.getMessageProperties().getDeliveryTag();
        log.error("【死信队列】AI 标签生成失败 3 次，请人工处理, pictureId={}", message.getPictureId());
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信 ack 失败", e);
        }
    }
}
