package com.zkxpicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zkxpicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.zkxpicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.zkxpicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.zkxpicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.zkxpicturebackend.manager.websocket.model.PictureEditResponseMessage;
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

@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    // final 只是保证 pictureSessions 这个引用变量不会指向另一个 Map 对象，
    // 但不禁止修改 Map 内部的数据（即可以调用 put、remove 等方法增减键值对）

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();


    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            log.info("获取编辑锁成功, pictureId={}, userId={}", pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        } else {
            log.warn("编辑锁冲突, pictureId={}, 当前编辑者={}, 请求者={}", pictureId, pictureEditingUsers.get(pictureId), user.getId());
        }
    }

    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            log.info("释放编辑锁, pictureId={}, userId={}", pictureId, user.getId());
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * WebSocket 连接建立成功时触发的回调方法
     * @param session 新建立的 WebSocket 会话
     * @throws Exception 处理过程中可能抛出的异常
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 从会话属性中获取该连接绑定的用户对象（握手时通过拦截器存入）
        User user = (User) session.getAttributes().get("user");
        // 从会话属性中获取该连接绑定的图片ID
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        log.info("WebSocket连接建立-协同编辑, userId={}, pictureId={}", user.getId(), pictureId);

        // 如果全局 Map 中该图片对应的会话集合不存在，则创建一个线程安全的 Set 并放入
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        // 获取该图片的会话集合，并将当前会话添加到集合中
        pictureSessions.get(pictureId).add(session);

        // 构造一个“信息”类型的响应消息，通知该图片房间内的其他用户：有人加入了
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        // 设置消息类型为普通信息（INFO）
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        // 格式化消息文本，例如“张三加入编辑”
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        // 设置用户视图对象（包含用户头像、昵称等前端展示信息）
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 向该图片房间内的所有用户广播“加入编辑”的消息（包括刚加入的自己，让其也能看到欢迎信息）
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * WebSocket 连接关闭时触发的回调方法
     * @param session   断开的 WebSocket 会话
     * @param status    关闭状态信息（正常关闭、错误等）
     * @throws Exception 处理过程中可能抛出的异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        // 从会话属性中获取该连接绑定的公共参数（在握手时通过拦截器存入）
        Map<String, Object> attributes = session.getAttributes();
        // 获取当前会话对应的图片ID
        Long pictureId = (Long) attributes.get("pictureId");
        // 获取当前会话对应的用户对象
        User user = (User) attributes.get("user");

        log.info("WebSocket连接关闭-协同编辑, userId={}, pictureId={}", user.getId(), pictureId);

        // 调用退出编辑消息的处理方法，释放当前用户对该图片的编辑权（如果有）
        // 第一个参数为 null，因为连接关闭不需要传递具体的请求消息
        handleExitEditMessage(null, session, user, pictureId);

        // 从全局会话容器中获取该图片对应的所有 WebSocket 会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        // 如果集合不为空，说明该图片房间内还有其他会话
        if (sessionSet != null) {
            // 从集合中移除当前断开的会话
            sessionSet.remove(session);
            // 如果移除后集合变为空，即没有其他用户查看/编辑这张图片了
            if (sessionSet.isEmpty()) {
                // 则从全局 Map 中删除该图片对应的键值对，释放内存
                pictureSessions.remove(pictureId);
            }
        }

        // 构造一个“信息”类型的响应消息，通知该图片房间内的其他用户：有人离开了
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        // 设置消息类型为普通信息（INFO），区别于进入编辑、退出编辑、编辑动作等类型
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        // 格式化消息文本，例如“张三离开编辑”
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        // 设置用户视图对象（可能包含用户头像、昵称等前端展示信息）
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 向该图片房间内的所有用户广播“离开编辑”的消息（不需要排除任何人）
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }
    /**
     * 向某张图片的所有 WebSocket 会话广播消息（可选择排除某个会话）
     * @param pictureId              图片ID，用于定位该图片的房间
     * @param pictureEditResponseMessage 要广播的消息内容
     * @param excludeSession         需要排除的会话（通常是不需要接收消息的发送者自己），可为null
     * @throws Exception 序列化或发送消息时可能抛出的异常
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        // 根据图片ID从全局会话Map中取出该图片对应的所有WebSocket会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);

        // 使用Hutool工具判断集合是否非空（既不为null也不为空集合）
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建Jackson的ObjectMapper对象，用于将Java对象序列化为JSON字符串
            ObjectMapper objectMapper = new ObjectMapper();

            // 创建一个简单的模块，用于注册自定义序列化器
            SimpleModule module = new SimpleModule();

            // 为Long类型（包装类）添加ToStringSerializer，序列化为字符串（避免前端JS精度丢失）
            module.addSerializer(Long.class, ToStringSerializer.instance);

            // 为long基本类型也添加同一个序列化器（统一处理）
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);

            // 将自定义模块注册到ObjectMapper中，使序列化配置生效
            objectMapper.registerModule(module);

            // 将响应消息对象序列化为JSON字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);

            // 将JSON字符串包装成WebSocket协议要求的TextMessage
            TextMessage textMessage = new TextMessage(message);

            // 遍历该图片房间内的所有WebSocket会话
            for (WebSocketSession session : sessionSet) {
                // 如果指定了排除会话且当前会话就是要排除的那个，则跳过，不发送消息
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                // 检查当前会话是否还处于打开状态（未关闭）
                if (session.isOpen()) {
                    try {
                        // 向该会话发送消息
                        session.sendMessage(textMessage);
                    } catch (Exception e) {
                        log.error("广播编辑消息失败, pictureId={}", pictureId, e);
                    }
                }
            }
        }
    }

    // 全部广播
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

}
