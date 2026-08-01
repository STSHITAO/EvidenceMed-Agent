package com.evidencemed.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "medical-agent.milvus.enabled=false",
        "medical-agent.bootstrap.demo-users-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:web-test;MODE=MySQL",
        "debug=false"
})
class SecurityWebTest {
    @Autowired WebTestClient client;

    @Test
    void protectsMedicalApi() {
        client.post().uri("/api/v1/consultations").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void exposesHealthEndpoint() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
