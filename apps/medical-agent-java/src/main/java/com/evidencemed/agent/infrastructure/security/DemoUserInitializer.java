package com.evidencemed.agent.infrastructure.security;

import com.evidencemed.agent.config.MedicalAgentProperties;
import com.evidencemed.agent.domain.user.UserAccount;
import com.evidencemed.agent.domain.user.UserRole;
import com.evidencemed.agent.infrastructure.persistence.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUserInitializer implements CommandLineRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final MedicalAgentProperties properties;

    public DemoUserInitializer(UserAccountRepository users, PasswordEncoder passwordEncoder,
                               MedicalAgentProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.getBootstrap().isDemoUsersEnabled()) return;
        createIfMissing("medical-user", properties.getBootstrap().getUserPassword(), UserRole.USER);
        createIfMissing("medical-admin", properties.getBootstrap().getAdminPassword(), UserRole.ADMIN);
    }

    private void createIfMissing(String username, String password, UserRole role) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(new UserAccount(username, passwordEncoder.encode(password), role));
        }
    }
}
