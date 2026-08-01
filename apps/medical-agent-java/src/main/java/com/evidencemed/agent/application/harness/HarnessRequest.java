package com.evidencemed.agent.application.harness;

public record HarnessRequest(String requestedSessionId, String question, byte[] image, String imageMediaType) {
}
