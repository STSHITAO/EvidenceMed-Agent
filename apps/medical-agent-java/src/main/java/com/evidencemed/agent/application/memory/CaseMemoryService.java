package com.evidencemed.agent.application.memory;

import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.casefile.CaseMessage;
import com.evidencemed.agent.infrastructure.persistence.CaseMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CaseMemoryService {
    private static final Logger log = LoggerFactory.getLogger(CaseMemoryService.class);
    private static final String KEY_PREFIX = "medical:case:";

    private final StringRedisTemplate redis;
    private final CaseMessageRepository messages;
    private final ObjectMapper mapper;
    private final MedicalAgentProperties properties;

    public CaseMemoryService(StringRedisTemplate redis, CaseMessageRepository messages,
                             ObjectMapper mapper, MedicalAgentProperties properties) {
        this.redis = redis;
        this.messages = messages;
        this.mapper = mapper;
        this.properties = properties;
    }

    public CaseMemorySnapshot load(String ownerId, String sessionId) {
        String key = key(ownerId, sessionId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, CaseMemorySnapshot.class);
        } catch (RedisConnectionFailureException | JsonProcessingException ex) {
            log.warn("Case memory cache unavailable; loading session {} from database", sessionId);
        }
        CaseMemorySnapshot snapshot = loadFromDatabase(sessionId);
        writeCache(key, snapshot);
        return snapshot;
    }

    public void refresh(String ownerId, String sessionId, String caseBrief) {
        CaseMemorySnapshot database = loadFromDatabase(sessionId);
        String brief = trim(caseBrief, properties.getMemory().getBriefMaxChars());
        writeCache(key(ownerId, sessionId), new CaseMemorySnapshot(brief, database.recentMessages()));
    }

    private CaseMemorySnapshot loadFromDatabase(String sessionId) {
        int limit = properties.getMemory().getHistoryLimit();
        List<CaseMessage> latest = new ArrayList<>(
                messages.findBySessionIdOrderByCreatedAtDesc(sessionId, PageRequest.of(0, limit)));
        Collections.reverse(latest);
        List<MemoryMessage> history = latest.stream()
                .map(item -> new MemoryMessage(item.getRole().name(), item.getContent(), item.getCreatedAt()))
                .toList();
        return new CaseMemorySnapshot(buildBrief(latest), history);
    }

    private String buildBrief(List<CaseMessage> history) {
        String joined = history.stream()
                .map(item -> item.getRole().name() + ": " + item.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return trim(joined, properties.getMemory().getBriefMaxChars());
    }

    private void writeCache(String key, CaseMemorySnapshot snapshot) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(snapshot),
                    Duration.ofHours(properties.getMemory().getTtlHours()));
        } catch (RedisConnectionFailureException | JsonProcessingException ex) {
            log.warn("Case memory cache write skipped");
        }
    }

    private String key(String ownerId, String sessionId) {
        return KEY_PREFIX + ownerId + ":" + sessionId;
    }

    private String trim(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(value.length() - max);
    }
}
