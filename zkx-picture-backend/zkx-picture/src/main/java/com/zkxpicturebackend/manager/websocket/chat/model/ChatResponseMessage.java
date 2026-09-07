package com.zkxpicturebackend.manager.websocket.chat.model;

import com.zkxpicturebackend.model.vo.UserVO;
import lombok.Data;

/**
 * 聊天响应消息（后端 → 前端）
 */
@Data
public class ChatResponseMessage {

    /**
     * 消息类型：SEND / JOIN / LEAVE / ERROR
     */
    private String type;

    /**
     * 聊天内容（或系统提示）
     */
    private String message;

    /**
     * 发送者用户信息
     */
    private UserVO user;

    /**
     * 创建时间
     */
    private Long createTime;
}
