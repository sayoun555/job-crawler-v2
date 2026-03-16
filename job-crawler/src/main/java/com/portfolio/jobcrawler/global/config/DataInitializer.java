package com.portfolio.jobcrawler.global.config;

import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.domain.user.repository.UserProfileRepository;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:#{null}}")
    private String adminEmail;

    @Value("${admin.password:#{null}}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminPassword == null) {
            log.info("[DataInitializer] 관리자 환경변수(ADMIN_EMAIL, ADMIN_PASSWORD) 미설정 — 자동 생성 건너뜀");
            return;
        }

        if (userRepository.findByEmail(adminEmail).isEmpty()) {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .nickname("관리자")
                    .build();
            admin.promoteToAdmin();
            userRepository.save(admin);
            userProfileRepository.save(UserProfile.builder().user(admin).build());
            log.info("[DataInitializer] 관리자 계정 생성 완료: {}", adminEmail);
        } else {
            log.info("[DataInitializer] 관리자 계정 이미 존재: {}", adminEmail);
        }
    }
}
