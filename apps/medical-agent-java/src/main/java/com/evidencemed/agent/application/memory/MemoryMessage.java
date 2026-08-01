package com.evidencemed.agent.application.memory;

import java.time.Instant;

public record MemoryMessage(String role, String content, Instant createdAt) {}
