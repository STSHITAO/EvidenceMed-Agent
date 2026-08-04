package com.evidencemed.agent.application.harness;

import com.evidencemed.agent.application.memory.CaseMemoryService;
import com.evidencemed.agent.application.runtime.AgentContext;
import com.evidencemed.agent.application.runtime.CollaborationBlackboard;
import com.evidencemed.agent.application.runtime.CoordinatorAgent;
import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.casefile.CaseMessage;
import com.evidencemed.agent.domain.casefile.CaseSession;
import com.evidencemed.agent.domain.casefile.MessageRole;
import com.evidencemed.agent.domain.report.MedicalReport;
import com.evidencemed.agent.domain.trace.AgentRunTrace;
import com.evidencemed.agent.domain.trace.RunStatus;
import com.evidencemed.agent.domain.user.UserAccount;
import com.evidencemed.agent.infrastructure.persistence.AgentRunTraceRepository;
import com.evidencemed.agent.infrastructure.persistence.CaseMessageRepository;
import com.evidencemed.agent.infrastructure.persistence.CaseSessionRepository;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import com.evidencemed.agent.infrastructure.persistence.MedicalReportRepository;
import com.evidencemed.agent.infrastructure.persistence.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
public class MedicalAgentHarness {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final UserAccountRepository users;
    private final CaseSessionRepository sessions;
    private final CaseMessageRepository messages;
    private final MedicalReportRepository reports;
    private final AgentRunTraceRepository traces;
    private final CollaborationEventRepository events;
    private final CoordinatorAgent coordinator;
    private final CaseMemoryService memory;
    private final ObjectMapper objectMapper;
    private final MedicalAgentProperties properties;

    public MedicalAgentHarness(UserAccountRepository users, CaseSessionRepository sessions,
                               CaseMessageRepository messages, MedicalReportRepository reports,
                               AgentRunTraceRepository traces, CollaborationEventRepository events,
                               CoordinatorAgent coordinator,
                               CaseMemoryService memory, ObjectMapper objectMapper,
                               MedicalAgentProperties properties) {
        this.users = users;
        this.sessions = sessions;
        this.messages = messages;
        this.reports = reports;
        this.traces = traces;
        this.events = events;
        this.coordinator = coordinator;
        this.memory = memory;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public HarnessResponse run(String username, HarnessRequest request) {
        validate(request);
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("认证用户不存在"));
        CaseSession session = resolveSession(user.getId(), request.requestedSessionId(), request.question());
        messages.save(new CaseMessage(session.getId(), MessageRole.USER, request.question(), hasImage(request)));

        AgentRunTrace trace = traces.save(new AgentRunTrace(session.getId(), user.getId(),
                inputHash(request), request.imageMediaType(), imageSize(request)));
        try {
            AgentContext context = new AgentContext(trace.getId(), user.getId(), session.getId(), request.question(),
                    request.image(), request.imageMediaType(), new CollaborationBlackboard(trace.getId(), events));
            coordinator.run(context);
            messages.save(new CaseMessage(session.getId(), MessageRole.ASSISTANT, context.getAnswer(), false));
            MedicalReport report = reports.save(new MedicalReport(session.getId(), trace.getId(), context.getAnswer(),
                    evidenceJson(context), context.getRiskLevel(), context.isHumanReviewRequired()));
            memory.refresh(user.getId(), session.getId(), context.getAnswer());
            trace.finish(context.getRag().degradations().isEmpty() ? RunStatus.SUCCEEDED : RunStatus.DEGRADED,
                    context.getRag().degradations().isEmpty() ? null : String.join(",", context.getRag().degradations()));
            traces.save(trace);
            return new HarnessResponse(session.getId(), report.getId(), trace.getId(), context.getAnswer(),
                    context.getRiskLevel(), context.isHumanReviewRequired(),
                    context.getSafetyReasons(),
                    context.getRag().evidence(), context.getRag().degradations());
        } catch (RuntimeException exception) {
            trace.finish(RunStatus.FAILED, exception.getClass().getSimpleName());
            traces.save(trace);
            throw exception;
        }
    }

    private CaseSession resolveSession(String userId, String requestedId, String question) {
        if (requestedId != null && !requestedId.isBlank()) {
            return sessions.findByIdAndOwnerId(requestedId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("病例会话不存在或无权访问"));
        }
        String title = question.strip().substring(0, Math.min(80, question.strip().length()));
        return sessions.save(new CaseSession(userId, title));
    }

    private void validate(HarnessRequest request) {
        if (request.question() == null || request.question().strip().length() < 2
                || request.question().length() > 3000) {
            throw new IllegalArgumentException("问题长度必须在 2 到 3000 字符之间");
        }
        if (hasImage(request)) {
            if (request.image().length > properties.getUpload().getMaxBytes()) {
                throw new IllegalArgumentException("影像超过大小限制");
            }
            if (!IMAGE_TYPES.contains(request.imageMediaType())) {
                throw new IllegalArgumentException("影像仅支持 JPEG 或 PNG");
            }
            if (!hasExpectedImageSignature(request.image(), request.imageMediaType())) {
                throw new IllegalArgumentException("影像内容与声明的 JPEG/PNG 类型不一致");
            }
        }
    }

    private String evidenceJson(AgentContext context) {
        try {
            return objectMapper.writeValueAsString(context.getRag().evidence());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("证据序列化失败", exception);
        }
    }

    private String inputHash(HarnessRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.question().getBytes(StandardCharsets.UTF_8));
            if (hasImage(request)) digest.update(request.image());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 不可用", impossible);
        }
    }

    private boolean hasImage(HarnessRequest request) {
        return request.image() != null && request.image().length > 0;
    }

    private Long imageSize(HarnessRequest request) {
        return hasImage(request) ? (long) request.image().length : null;
    }

    private boolean hasExpectedImageSignature(byte[] bytes, String mediaType) {
        if ("image/png".equals(mediaType)) {
            return bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                    && bytes[2] == 0x4e && bytes[3] == 0x47 && bytes[4] == 0x0d
                    && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        }
        return bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff;
    }
}
