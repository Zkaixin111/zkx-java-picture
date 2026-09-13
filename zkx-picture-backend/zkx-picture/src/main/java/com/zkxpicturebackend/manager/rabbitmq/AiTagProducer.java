package com.zkxpicturebackend.manager.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiTagProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendAiTagMessage(Long pictureId,String imageUrl) {

        AiTagMessage aiTagMessage = new AiTagMessage(pictureId, imageUrl);

        rabbitTemplate.convertAndSend(
                AiTagRabbitMQConfig.AI_TAG_EXCHANGE,
                AiTagRabbitMQConfig.AI_TAG_ROUTING_KEY,
                aiTagMessage
        );
        log.info("Sent message: {}", aiTagMessage);
    }
}
