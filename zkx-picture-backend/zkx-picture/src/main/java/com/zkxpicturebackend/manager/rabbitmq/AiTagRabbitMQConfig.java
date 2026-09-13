package com.zkxpicturebackend.manager.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class AiTagRabbitMQConfig {
    // 正常交换机/队列
    public static final String AI_TAG_EXCHANGE = "ai.tag.exchange";
    public static final String AI_TAG_QUEUE = "ai.tag.queue";
    public static final String AI_TAG_ROUTING_KEY = "ai.tag";
    // 死信交换机/队列
    public static final String AI_TAG_DLX_EXCHANGE = "ai.tag.dlx.exchange";
    public static final String AI_TAG_DLQ_QUEUE = "ai.tag.dlq.queue";
    public static final String AI_TAG_DLQ_ROUTING_KEY = "ai.tag.dlq";

    //    死信交换机/队列
    @Bean
    public DirectExchange aiTagDlxExchange() {
//        创建一个名为`AI_TAG_DLX_EXCHANGE`的直连死信交换机，**持久化，不会自动删除**
        return new DirectExchange(AI_TAG_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue aiTagDlqQueue() {
//        创建名字为 `AI_TAG_DLQ_QUEUE` 的**持久化、非排他、不自动删除**的死信队列。MQ 重启，队列还在。
        return QueueBuilder.durable(AI_TAG_DLQ_QUEUE).build();
    }

    @Bean
    public Binding aiTagDlqBinding() {
//        把【死信队列 DLQ】绑定到【死信交换机 DLX】，并且指定路由 key。
        return BindingBuilder.bind(aiTagDlqQueue())
                .to(aiTagDlxExchange())
                .with(AI_TAG_DLQ_ROUTING_KEY);
    }

//    正常交换机/队列
    @Bean
    public DirectExchange aiTagExchange() {
        return new DirectExchange(AI_TAG_EXCHANGE, true, false);
    }

    //    给队列绑定死信相关参数 + 设置消息 TTL。
    @Bean
    public Queue aiTagQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", AI_TAG_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", AI_TAG_DLQ_ROUTING_KEY);
        args.put("x-message-ttl", 86400000); // 24小时过期
        return QueueBuilder.durable(AI_TAG_QUEUE).withArguments(args).build();
    }
    @Bean
    public Binding aiTagBinding() {
        return BindingBuilder.bind(aiTagQueue())
                .to(aiTagExchange())
                .with(AI_TAG_ROUTING_KEY);
    }
}
