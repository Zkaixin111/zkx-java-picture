package com.zkxpicturebackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.lmax.disruptor.WorkHandler;
import com.zkxpicturebackend.manager.websocket.PictureEditHandler;
import com.zkxpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.zkxpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.zkxpicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.zkxpicturebackend.model.entity.User;
import com.zkxpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

@Slf4j
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Resource
    private UserService userService;

    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        // 获取到消息类别
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum;
        try {
            pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);
        } catch (Exception e) {
            // 非法消息类型：给客户端返回错误，不能抛异常中断 Disruptor worker 线程
            log.warn("收到非法图片编辑消息类型: pictureId={}, type={}", pictureId, type);
            try {
                PictureEditResponseMessage errorResponse = new PictureEditResponseMessage();
                errorResponse.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                errorResponse.setMessage("消息类型错误");
                if (user != null) {
                    errorResponse.setUser(userService.getUserVO(user));
                }
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(errorResponse)));
            } catch (Exception sendEx) {
                log.error("发送非法消息类型错误提示失败", sendEx);
            }
            return;
        }
        // 调用对应的消息处理方法
        try {
            switch (pictureEditMessageTypeEnum) {
                case ENTER_EDIT:
                    pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                    break;
                case EDIT_ACTION:
                    pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                    break;
                case EXIT_EDIT:
                    pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                    break;
                default:
                    PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                    pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                    pictureEditResponseMessage.setMessage("消息类型错误");
                    if (user != null) {
                        pictureEditResponseMessage.setUser(userService.getUserVO(user));
                    }
                    session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
            }
        } catch (Exception e) {
            // 单个会话处理异常不能中断 Disruptor worker 线程
            log.error("处理图片编辑事件失败, pictureId={}, type={}", pictureId, type, e);
        }
    }
}
