package com.zkxpicturebackend.manager.websocket.chat.model;

import lombok.Getter;

/**
 * 聊天消息类型枚举
 */
@Getter
public enum ChatMessageTypeEnum {

    SEND("SEND", "发送消息"),

    JOIN("JOIN", "加入空间"),

    LEAVE("LEAVE", "离开空间"),

    ERROR("ERROR", "错误提示");

    private final String value;

    private final String text;

    ChatMessageTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举
     */
    public static ChatMessageTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (ChatMessageTypeEnum typeEnum : ChatMessageTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}
