package com.zkxpicturebackend.manager.websocket.chat.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatRequestMessage;
import com.zkxpicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 聊天事件生产者：把消息发布进 Disruptor 环形缓冲区
 */
@Slf4j
@Component
public class ChatEventProducer {

    @Resource
    private Disruptor<ChatEvent> chatEventDisruptor;

    public void publishEvent(ChatRequestMessage chatRequestMessage, WebSocketSession session, User user, Long spaceId) {
        chatEventDisruptor.publishEvent((event, sequence) -> {
            event.setChatRequestMessage(chatRequestMessage);
            event.setSession(session);
            event.setUser(user);
            event.setSpaceId(spaceId);
        });
    }
}
