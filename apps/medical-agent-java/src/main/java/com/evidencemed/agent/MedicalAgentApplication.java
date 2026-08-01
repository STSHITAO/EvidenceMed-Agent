package com.evidencemed.agent;

import com.evidencemed.agent.config.MedicalAgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MedicalAgentProperties.class)
public class MedicalAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedicalAgentApplication.class, args);
    }
}
