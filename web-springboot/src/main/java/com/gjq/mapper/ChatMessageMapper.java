package com.gjq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gjq.dto.ChatDailyCountDTO;
import com.gjq.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    long countActiveMessages();

    List<ChatDailyCountDTO> countMessagesByDaySince(@Param("start") LocalDateTime start);
}
