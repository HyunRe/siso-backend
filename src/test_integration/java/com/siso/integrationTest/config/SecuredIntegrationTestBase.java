package com.siso.integrationTest.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증이 필요한 통합 테스트 기본 클래스 (JWT 필터 활성화)
 *
 * Security: TestSecurityWithAuthConfig (anyRequest().authenticated())
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import({
        TestSecurityWithAuthConfig.class,
        TestAwsConfig.class
})
public abstract class SecuredIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedMySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", SharedMySQLContainer::getUsername);
        registry.add("spring.datasource.password", SharedMySQLContainer::getPassword);
    }
}
