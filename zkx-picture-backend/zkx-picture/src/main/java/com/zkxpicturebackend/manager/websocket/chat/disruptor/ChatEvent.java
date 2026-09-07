package com.zkxpicturebackend.manager.websocket.chat.disruptor;

import com.zkxpicturebackend.manager.websocket.chat.model.ChatRequestMessage;
import com.zkxpicturebackend.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 聊天事件（Disruptor 环形缓冲区中的事件对象，预分配复用）
 */
@Data
public class ChatEvent {

    /**
     * 聊天请求消息
     */
    private ChatRequestMessage chatRequestMessage;

    /**
     * 当前连接会话
     */
    private WebSocketSession session;

    /**
     * 当前用户
     */
    private User user;

    /**
     * 空间 id
     */
    private Long spaceId;
}
