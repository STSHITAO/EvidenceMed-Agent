package com.evidencemed.agent.application.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalSkillRegistryTest {
    @Test
    void loadsSkillMarkdownAndSelectsEmergencyPlan() {
        MedicalSkillRegistry registry = new MedicalSkillRegistry();
        registry.reload();

        assertThat(registry.all()).hasSize(3);
        assertThat(registry.select("患者剧烈胸痛并呼吸困难"))
                .extracting(MedicalSkill::id)
                .contains("urgent-care");
    }
}
