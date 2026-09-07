package com.zkxpicturebackend.manager.websocket.chat.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 聊天事件 Disruptor 配置（无锁环形缓冲 + 事件对象预分配）
 */
@Configuration
public class ChatEventDisruptorConfig {

    @Resource
    private ChatEventWorkHandler chatEventWorkHandler;

    @Bean("chatEventDisruptor")
    public Disruptor<ChatEvent> chatEventDisruptor() {
        int bufferSize = 1024 * 256;
        Disruptor<ChatEvent> disruptor = new Disruptor<>(
                ChatEvent::new,
                bufferSize,
                ThreadFactoryBuilder.create().setNamePrefix("chatEventDisruptor").build()
        );
        // 竞争消费：每条消息只被一个 worker 处理
        disruptor.handleEventsWithWorkerPool(chatEventWorkHandler);
        disruptor.start();
        return disruptor;
    }
}
