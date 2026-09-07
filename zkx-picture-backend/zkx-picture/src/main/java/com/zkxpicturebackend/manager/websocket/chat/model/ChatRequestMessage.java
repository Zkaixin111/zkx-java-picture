package com.zkxpicturebackend.manager.websocket.chat.model;

import lombok.Data;

/**
 * 聊天请求消息（前端 → 后端）
 */
@Data
public class ChatRequestMessage {

    /**
     * 消息类型：SEND / JOIN / LEAVE
     */
    private String type;

    /**
     * 聊天内容
     */
    private String content;
}
