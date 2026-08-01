package com.evidencemed.agent.application.memory;

import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.casefile.CaseMessage;
import com.evidencemed.agent.domain.casefile.MessageRole;
import com.evidencemed.agent.infrastructure.persistence.CaseMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaseMemoryServiceTest {
    @Test
    void fallsBackToDatabaseWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(any())).thenThrow(new RedisConnectionFailureException("offline"));

        CaseMessageRepository repository = mock(CaseMessageRepository.class);
        when(repository.findBySessionIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(new CaseMessage("case-1", MessageRole.USER, "胸痛两小时", false)));

        CaseMemoryService service = new CaseMemoryService(
                redis, repository, new ObjectMapper().findAndRegisterModules(), new MedicalAgentProperties());

        CaseMemorySnapshot result = service.load("user-1", "case-1");

        assertThat(result.caseBrief()).contains("胸痛两小时");
        assertThat(result.recentMessages()).hasSize(1);
    }
}
