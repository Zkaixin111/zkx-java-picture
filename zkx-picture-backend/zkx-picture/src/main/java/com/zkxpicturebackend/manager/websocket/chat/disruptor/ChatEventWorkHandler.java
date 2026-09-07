package com.zkxpicturebackend.manager.websocket.chat.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.zkxpicturebackend.manager.websocket.chat.SpaceChatHandler;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatMessageTypeEnum;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatRequestMessage;
import com.zkxpicturebackend.manager.websocket.chat.model.ChatResponseMessage;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 聊天事件消费者（Worker 竞争消费，每条消息只处理一次）
 * 异常隔离：单条消息处理失败不中断 worker 线程
 */
@Slf4j
@Component
public class ChatEventWorkHandler implements WorkHandler<ChatEvent> {

    @Resource
    @Lazy
    private SpaceChatHandler spaceChatHandler;

    @Resource
    private UserService userService;

    @Override
    public void onEvent(ChatEvent event) throws Exception {
        ChatRequestMessage chatRequestMessage = event.getChatRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long spaceId = event.getSpaceId();

        String type = chatRequestMessage.getType();
        ChatMessageTypeEnum chatMessageTypeEnum;
        try {
            chatMessageTypeEnum = ChatMessageTypeEnum.getEnumByValue(type);
        } catch (Exception e) {
            sendError(session, user, "消息类型错误");
            return;
        }
        if (chatMessageTypeEnum == null) {
            sendError(session, user, "消息类型错误");
            return;
        }
        try {
            switch (chatMessageTypeEnum) {
                case SEND:
                    spaceChatHandler.handleSendMessage(chatRequestMessage, session, user, spaceId);
                    break;
                default:
                    sendError(session, user, "消息类型错误");
            }
        } catch (Exception e) {
            // 单个会话处理异常不能中断 Disruptor worker 线程
            log.error("处理聊天事件失败, spaceId={}, type={}", spaceId, type, e);
        }
    }

    private void sendError(WebSocketSession session, User user, String message) {
        try {
            ChatResponseMessage errorResponse = new ChatResponseMessage();
            errorResponse.setType(ChatMessageTypeEnum.ERROR.getValue());
            errorResponse.setMessage(message);
            if (user != null) {
                errorResponse.setUser(userService.getUserVO(user));
            }
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(errorResponse)));
        } catch (Exception sendEx) {
            log.error("发送聊天错误提示失败", sendEx);
        }
    }
}
