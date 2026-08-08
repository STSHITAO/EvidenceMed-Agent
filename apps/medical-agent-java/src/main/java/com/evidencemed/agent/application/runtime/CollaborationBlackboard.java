package com.evidencemed.agent.application.runtime;

import com.evidencemed.agent.domain.trace.CollaborationEvent;
import com.evidencemed.agent.infrastructure.persistence.CollaborationEventRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

public class CollaborationBlackboard {
    private final String traceId;
    private final CollaborationEventRepository events;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
    private final Map<String, TaskEntry> tasks = new LinkedHashMap<>();

    public CollaborationBlackboard(String traceId, CollaborationEventRepository events) {
        this.traceId = traceId;
        this.events = events;
    }

    public synchronized boolean createTask(AgentTask task) {
        if (tasks.containsKey(task.id())) return false;
        tasks.put(task.id(), new TaskEntry(task));
        record("Coordinator", "TASK_CREATED", task.id(),
                task.type() + ", dependencies=" + task.dependencies());
        return true;
    }

    public synchronized List<AgentTask> readyTasks() {
        return tasks.values().stream()
                .filter(entry -> entry.status == TaskStatus.OPEN)
                .map(entry -> entry.task)
                .filter(task -> task.dependencies().stream().allMatch(this::isCompleted))
                .sorted(Comparator.comparingInt(AgentTask::priority).reversed()
                        .thenComparing(AgentTask::id))
                .toList();
    }

    public synchronized void claimTask(String taskId, String actor) {
        TaskEntry entry = requiredTask(taskId);
        if (entry.status != TaskStatus.OPEN) {
            throw new IllegalStateException("任务不可认领: " + taskId + " status=" + entry.status);
        }
        entry.status = TaskStatus.CLAIMED;
        record(actor, "TASK_CLAIMED", taskId, entry.task.type().name());
    }

    public synchronized void completeTask(String taskId, String actor, String summary) {
        TaskEntry entry = requiredTask(taskId);
        if (entry.status != TaskStatus.CLAIMED) {
            throw new IllegalStateException("任务尚未被认领: " + taskId);
        }
        entry.status = TaskStatus.COMPLETED;
        record(actor, "TASK_COMPLETED", taskId, summary);
    }

    public synchronized void failTask(String taskId, String actor, String summary) {
        TaskEntry entry = requiredTask(taskId);
        entry.status = TaskStatus.FAILED;
        record(actor, "TASK_FAILED", taskId, summary);
    }

    public synchronized boolean hasTask(String taskId) { return tasks.containsKey(taskId); }
    public synchronized boolean isCompleted(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        return entry != null && entry.status == TaskStatus.COMPLETED;
    }
    public synchronized TaskStatus taskStatus(String taskId) { return requiredTask(taskId).status; }

    public synchronized void publish(String agent, String artifact, Object value, String summary) {
        if (value instanceof byte[]) {
            throw new IllegalArgumentException("协作黑板禁止保存原始影像字节");
        }
        artifacts.put(artifact, value);
        record(agent, "ARTIFACT_PUBLISHED", artifact, summary);
    }

    public synchronized Map<String, Object> snapshot() { return Map.copyOf(artifacts); }

    private TaskEntry requiredTask(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) throw new IllegalArgumentException("未知任务: " + taskId);
        return entry;
    }

    private void record(String actor, String eventType, String name, String summary) {
        events.save(new CollaborationEvent(traceId, sequence.incrementAndGet(), actor,
                eventType, name, summary));
    }

    private static final class TaskEntry {
        private final AgentTask task;
        private TaskStatus status = TaskStatus.OPEN;
        private TaskEntry(AgentTask task) { this.task = task; }
    }
}
