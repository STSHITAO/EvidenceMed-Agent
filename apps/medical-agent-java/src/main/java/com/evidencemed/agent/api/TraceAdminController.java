package com.evidencemed.agent.api;

import com.evidencemed.agent.domain.trace.AgentRunTrace;
import com.evidencemed.agent.domain.trace.AgentStep;
import com.evidencemed.agent.domain.trace.CollaborationEvent;
import com.evidencemed.agent.infrastructure.persistence.AgentRunTraceRepository;
import com.evidencemed.agent.infrastructure.persistence.AgentStepRepository;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/traces")
public class TraceAdminController {
    private final AgentRunTraceRepository traces;
    private final AgentStepRepository steps;
    private final CollaborationEventRepository events;

    public TraceAdminController(AgentRunTraceRepository traces, AgentStepRepository steps,
                                CollaborationEventRepository events) {
        this.traces = traces;
        this.steps = steps;
        this.events = events;
    }

    @GetMapping("/{traceId}")
    public Mono<TraceDetails> find(@PathVariable String traceId) {
        return Mono.fromCallable(() -> {
            AgentRunTrace trace = traces.findById(traceId)
                    .orElseThrow(() -> new IllegalArgumentException("运行轨迹不存在"));
            return new TraceDetails(trace, steps.findByTraceIdOrderByStartedAt(traceId),
                    events.findByTraceIdOrderBySequenceNumber(traceId));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public record TraceDetails(AgentRunTrace trace, List<AgentStep> steps,
                               List<CollaborationEvent> events) {
    }
}
