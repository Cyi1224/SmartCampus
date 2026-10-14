package com.simon.campus.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.simon.campus.common.BizException;
import com.simon.campus.mapper.ChatMessageMapper;
import com.simon.campus.mapper.ChatSessionMapper;
import com.simon.campus.mapper.HumanTicketMapper;
import com.simon.campus.model.entity.ChatMessage;
import com.simon.campus.model.entity.ChatSession;
import com.simon.campus.model.entity.HumanTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HumanHandoffService {

    private static final List<String> OPEN_STATUSES = List.of("PENDING", "HANDLING");

    private final HumanTicketMapper ticketMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionMapper chatSessionMapper;

    public HumanTicket findOpenTicket(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return null;
        return ticketMapper.selectOne(new LambdaQueryWrapper<HumanTicket>()
            .eq(HumanTicket::getSessionId, sessionId)
            .in(HumanTicket::getStatus, OPEN_STATUSES)
            .orderByDesc(HumanTicket::getCreatedAt)
            .last("LIMIT 1"));
    }

    public HumanTicket requestHandoff(String sessionId, Long userId, String summary, String urgency) {
        return requestHandoff(sessionId, userId, summary, urgency, true);
    }

    public HumanTicket requestHandoff(String sessionId, Long userId, String summary, String urgency, boolean appendNoticeMessages) {
        if (!StringUtils.hasText(sessionId)) throw new BizException("会话不存在，无法转人工");
        HumanTicket existing = findOpenTicket(sessionId);
        if (existing != null) return existing;

        ensureSession(sessionId, userId, "人工服务请求");

        String content = StringUtils.hasText(summary) ? summary : "申请转人工";
        if (appendNoticeMessages) {
            appendMessage(sessionId, userId, "user", content, "HUMAN_HANDOFF");
            appendMessage(sessionId, userId, "assistant",
                "已为你转接人工老师。你可以继续在这里补充问题，老师会看到当前会话记录并回复。",
                "HUMAN_HANDOFF");
            touchSession(sessionId, 2);
        }

        HumanTicket ticket = new HumanTicket();
        ticket.setTicketNo("TK" + System.currentTimeMillis());
        ticket.setSessionId(sessionId);
        ticket.setUserId(userId != null ? userId : 0L);
        ticket.setSubject(content.length() > 100 ? content.substring(0, 100) : content);
        ticket.setIssueType("人工服务");
        ticket.setAiSummary("用户申请转人工。老师可查看该会话中的学生与 AI 历史记录，并在同一会话回复。");
        ticket.setUrgency(normalizeUrgency(urgency));
        ticket.setStatus("PENDING");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        ticketMapper.insert(ticket);
        return ticket;
    }

    public void appendStudentMessage(String sessionId, Long userId, String content) {
        appendMessage(sessionId, userId, "user", content, "HUMAN_HANDOFF");
        touchSession(sessionId, 1);
        HumanTicket ticket = findOpenTicket(sessionId);
        if (ticket != null) {
            ticketMapper.update(null, new UpdateWrapper<HumanTicket>()
                .eq("id", ticket.getId())
                .set("updated_at", LocalDateTime.now()));
        }
    }

    public void cancelHandoff(String sessionId, Long userId) {
        HumanTicket ticket = findOpenTicket(sessionId);
        if (ticket == null) return;
        ticketMapper.update(null, new UpdateWrapper<HumanTicket>()
            .eq("id", ticket.getId())
            .set("status", "CLOSED")
            .set("updated_at", LocalDateTime.now()));
        appendMessage(sessionId, userId, "assistant",
            "人工服务已退出，已为你切换回 AI 助手模式。", "SYSTEM");
        touchSession(sessionId, 1);
    }

    public ChatMessage reply(Long ticketId, Long teacherId, String content) {
        if (!StringUtils.hasText(content)) throw new BizException("回复内容不能为空");
        HumanTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) throw new BizException(404, "工单不存在");

        ChatMessage message = appendMessage(ticket.getSessionId(), teacherId, "teacher", content, "HUMAN_HANDOFF");
        touchSession(ticket.getSessionId(), 1);
        ticketMapper.update(null, new UpdateWrapper<HumanTicket>()
            .eq("id", ticketId)
            .set("status", "HANDLING")
            .set("updated_at", LocalDateTime.now()));
        return message;
    }

    private void ensureSession(String sessionId, Long userId, String title) {
        if (chatSessionMapper.selectById(sessionId) != null) return;
        ChatSession session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId != null ? userId : 0L);
        session.setTitle(title);
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);
    }

    private ChatMessage appendMessage(String sessionId, Long userId, String role, String content, String intent) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);
        return message;
    }

    private void touchSession(String sessionId, int addedMessages) {
        chatSessionMapper.update(null, new UpdateWrapper<ChatSession>()
            .eq("session_id", sessionId)
            .setSql("message_count = message_count + " + addedMessages + ", updated_at = NOW()"));
    }

    private String normalizeUrgency(String urgency) {
        if (urgency == null) return "MEDIUM";
        return switch (urgency.toLowerCase()) {
            case "high", "高", "紧急" -> "HIGH";
            case "low", "低" -> "LOW";
            default -> "MEDIUM";
        };
    }
}
