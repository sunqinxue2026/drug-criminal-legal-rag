package com.gjq.service.support;

import com.gjq.dto.ChatDailyCountDTO;
import com.gjq.mapper.ChatMessageMapper;
import com.gjq.mapper.ChatSessionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatStatisticsService {

    private static final DateTimeFormatter STORAGE_KEY_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("M月d日");

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatStatisticsService(ChatSessionMapper chatSessionMapper, ChatMessageMapper chatMessageMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    public Map<String, Object> getChatStats() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        LocalDateTime startTime = startDate.atStartOfDay();

        Map<String, Integer> sessionTrend = initRecentTrend(startDate, today);
        Map<String, Integer> messageTrend = initRecentTrend(startDate, today);

        mergeCounts(sessionTrend, chatSessionMapper.countSessionsByDaySince(startTime));
        mergeCounts(messageTrend, chatMessageMapper.countMessagesByDaySince(startTime));

        Map<String, Object> result = new HashMap<>();
        result.put("sessionCount", chatSessionMapper.countActiveSessions());
        result.put("messageCount", chatMessageMapper.countActiveMessages());
        result.put("dates", formatDisplayDates(sessionTrend.keySet()));
        result.put("sessionTrend", new ArrayList<>(sessionTrend.values()));
        result.put("messageTrend", new ArrayList<>(messageTrend.values()));
        return result;
    }

    private Map<String, Integer> initRecentTrend(LocalDate startDate, LocalDate endDate) {
        Map<String, Integer> trend = new LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            trend.put(STORAGE_KEY_FORMATTER.format(cursor), 0);
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    private void mergeCounts(Map<String, Integer> trend, List<ChatDailyCountDTO> rows) {
        for (ChatDailyCountDTO row : rows) {
            if (row.getDayKey() == null || row.getTotal() == null || !trend.containsKey(row.getDayKey())) {
                continue;
            }
            trend.put(row.getDayKey(), row.getTotal());
        }
    }

    private List<String> formatDisplayDates(Iterable<String> dateKeys) {
        List<String> display = new ArrayList<>();
        for (String dateKey : dateKeys) {
            display.add(DISPLAY_FORMATTER.format(LocalDate.parse(dateKey, STORAGE_KEY_FORMATTER)));
        }
        return display;
    }
}
