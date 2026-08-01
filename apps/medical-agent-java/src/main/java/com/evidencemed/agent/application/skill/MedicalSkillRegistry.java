package com.evidencemed.agent.application.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class MedicalSkillRegistry {
    private volatile List<MedicalSkill> skills = List.of();

    @PostConstruct
    public void reload() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:skills/*/SKILL.md");
            List<MedicalSkill> loaded = new ArrayList<>();
            for (Resource resource : resources) loaded.add(parse(resource));
            skills = List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("医疗 Skill 加载失败", exception);
        }
    }

    public List<MedicalSkill> select(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return skills.stream().filter(skill -> skill.triggers().stream()
                .anyMatch(trigger -> normalized.contains(trigger.toLowerCase(Locale.ROOT)))).toList();
    }

    public List<MedicalSkill> all() { return skills; }

    private MedicalSkill parse(Resource resource) throws IOException {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        String name = content.lines().filter(line -> line.startsWith("# ")).findFirst()
                .map(line -> line.substring(2).trim()).orElse(resource.getFilename());
        String triggerLine = content.lines().filter(line -> line.startsWith("triggers:")).findFirst().orElse("triggers:");
        List<String> triggers = java.util.Arrays.stream(triggerLine.substring("triggers:".length()).split(","))
                .map(String::trim).filter(value -> !value.isBlank()).toList();
        String path = resource.getURL().getPath();
        String parent = path.substring(0, path.lastIndexOf('/'));
        String id = parent.substring(parent.lastIndexOf('/') + 1);
        return new MedicalSkill(id, name, triggers, content);
    }
}
