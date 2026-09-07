package com.zkxpicturebackend.config;

import com.zkxpicturebackend.manager.websocket.PictureEditHandler;
import com.zkxpicturebackend.manager.websocket.WsHandshakeInterceptor;
import com.zkxpicturebackend.manager.websocket.chat.SpaceChatHandler;
import com.zkxpicturebackend.manager.websocket.chat.WsChatHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private PictureEditHandler pictureEditHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Resource
    private SpaceChatHandler spaceChatHandler;

    @Resource
    private WsChatHandshakeInterceptor wsChatHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 图片协同编辑
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOrigins("*");
        // 团队空间聊天
        registry.addHandler(spaceChatHandler, "/ws/space/chat")
                .addInterceptors(wsChatHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
