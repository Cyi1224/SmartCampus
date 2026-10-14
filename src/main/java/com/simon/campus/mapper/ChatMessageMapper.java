package com.simon.campus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.simon.campus.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
