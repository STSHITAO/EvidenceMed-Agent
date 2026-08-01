package com.evidencemed.agent.api;

import com.evidencemed.agent.application.skill.MedicalSkill;
import com.evidencemed.agent.application.skill.MedicalSkillRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/skills")
public class SkillAdminController {
    private final MedicalSkillRegistry registry;
    public SkillAdminController(MedicalSkillRegistry registry) { this.registry = registry; }

    @GetMapping
    public List<SkillSummary> list() { return registry.all().stream().map(SkillSummary::from).toList(); }

    @PostMapping("/reload")
    public List<SkillSummary> reload() { registry.reload(); return list(); }

    public record SkillSummary(String id, String name, List<String> triggers) {
        static SkillSummary from(MedicalSkill skill) {
            return new SkillSummary(skill.id(), skill.name(), skill.triggers());
        }
    }
}
