package com.gjq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gjq.dto.ChatDailyCountDTO;
import com.gjq.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    long countActiveSessions();

    List<ChatDailyCountDTO> countSessionsByDaySince(@Param("start") LocalDateTime start);
}
