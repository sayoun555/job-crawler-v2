package com.portfolio.jobcrawler.global.config;

import com.portfolio.jobcrawler.domain.user.entity.User;
import com.portfolio.jobcrawler.domain.user.entity.UserProfile;
import com.portfolio.jobcrawler.domain.user.repository.UserProfileRepository;
import com.portfolio.jobcrawler.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 시작 시 관리자 계정이 없으면 자동 생성.
 * email: admin@job.com / password: admin1234
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("admin@job.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@job.com")
                    .password(passwordEncoder.encode("admin1234"))
                    .nickname("관리자")
                    .build();
            // role은 기본 USER → ADMIN으로 변경
            admin.promoteToAdmin();
            userRepository.save(admin);
            userProfileRepository.save(UserProfile.builder().user(admin).build());
            log.info("[DataInitializer] 관리자 계정 생성 완료: admin@job.com / admin1234");
        } else {
            log.info("[DataInitializer] 관리자 계정 이미 존재");
        }
    }
}
