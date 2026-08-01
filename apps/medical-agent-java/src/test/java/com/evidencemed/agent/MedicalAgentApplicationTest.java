package com.evidencemed.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "medical-agent.milvus.enabled=false",
        "medical-agent.bootstrap.demo-users-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:context-test;MODE=MySQL",
        "debug=false"
})
class MedicalAgentApplicationTest {
    @Test
    void contextLoads() {}
}
