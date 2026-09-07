package com.zkxpicturebackend.manager.websocket.chat;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zkxpicturebackend.manager.websocket.chat.disruptor.ChatEventProducer;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatMessageTypeEnum;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatRequestMessage;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatResponseMessage;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 团队空间聊天 WebSocket 处理器
 * 房间粒度：spaceId（一个空间 = 一个聊天室）
 */
@Slf4j
@Component
public class SpaceChatHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    // 空间聊天室：key: spaceId, value: 该空间所有在线会话集合
    private final Map<Long, Set<WebSocketSession>> spaceSessions = new ConcurrentHashMap<>();

    @Resource
    @Lazy
    private ChatEventProducer chatEventProducer;

    /**
     * 收到前端消息 → 发布到 Disruptor 异步处理（不阻塞 IO 线程）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ChatRequestMessage chatRequestMessage = JSONUtil.toBean(message.getPayload(), ChatRequestMessage.class);
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long spaceId = (Long) attributes.get("spaceId");
        chatEventProducer.publishEvent(chatRequestMessage, session, user, spaceId);
    }

    /**
     * 处理发送消息（在 Disruptor 消费者线程中执行）
     */
    public void handleSendMessage(ChatRequestMessage chatRequestMessage, WebSocketSession session, User user, Long spaceId) throws Exception {
        ChatResponseMessage chatResponseMessage = new ChatResponseMessage();
        chatResponseMessage.setType(ChatMessageTypeEnum.SEND.getValue());
        chatResponseMessage.setMessage(chatRequestMessage.getContent());
        chatResponseMessage.setUser(userService.getUserVO(user));
        chatResponseMessage.setCreateTime(System.currentTimeMillis());
        // 广播给空间内所有人（含自己，前端需要回显自己发的消息）
        broadcastToSpace(spaceId, chatResponseMessage);
    }

    /**
     * 连接建立：加入空间聊天室 + 广播"xx加入空间"
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        Long spaceId = (Long) session.getAttributes().get("spaceId");
        spaceSessions.putIfAbsent(spaceId, ConcurrentHashMap.newKeySet());
        spaceSessions.get(spaceId).add(session);

        ChatResponseMessage chatResponseMessage = new ChatResponseMessage();
        chatResponseMessage.setType(ChatMessageTypeEnum.JOIN.getValue());
        chatResponseMessage.setMessage(String.format("%s 加入了空间", user.getUserName()));
        chatResponseMessage.setUser(userService.getUserVO(user));
        chatResponseMessage.setCreateTime(System.currentTimeMillis());
        broadcastToSpace(spaceId, chatResponseMessage);
    }

    /**
     * 连接关闭：移出聊天室 + 广播"xx离开空间"（防止会话泄漏）
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long spaceId = (Long) attributes.get("spaceId");
        User user = (User) attributes.get("user");

        Set<WebSocketSession> sessionSet = spaceSessions.get(spaceId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                spaceSessions.remove(spaceId);   // 房间空了，释放内存
            }
        }

        ChatResponseMessage chatResponseMessage = new ChatResponseMessage();
        chatResponseMessage.setType(ChatMessageTypeEnum.LEAVE.getValue());
        chatResponseMessage.setMessage(String.format("%s 离开了空间", user.getUserName()));
        chatResponseMessage.setUser(userService.getUserVO(user));
        chatResponseMessage.setCreateTime(System.currentTimeMillis());
        broadcastToSpace(spaceId, chatResponseMessage);
    }

    /**
     * 广播给空间内所有在线会话（聊天需要广播给自己，前端回显）
     */
    private void broadcastToSpace(Long spaceId, ChatResponseMessage chatResponseMessage) throws Exception {
        Set<WebSocketSession> sessionSet = spaceSessions.get(spaceId);
        if (CollUtil.isEmpty(sessionSet)) {
            return;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        // Long 序列化为字符串，防前端 JS 精度丢失
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        objectMapper.registerModule(module);
        String message = objectMapper.writeValueAsString(chatResponseMessage);
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessionSet) {
            if (session.isOpen()) {
                session.sendMessage(textMessage);
            }
        }
    }
}
