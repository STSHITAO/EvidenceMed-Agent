package com.evidencemed.agent.application.skill;

import java.util.List;

public record MedicalSkill(String id, String name, List<String> triggers, String instructions) {
    public MedicalSkill { triggers = List.copyOf(triggers); }
}
