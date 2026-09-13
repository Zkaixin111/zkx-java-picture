package com.zkxpicturebackend.manager.rabbitmq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTagMessage implements Serializable {
    private Long pictureId;
    private String imageUrl;
}
