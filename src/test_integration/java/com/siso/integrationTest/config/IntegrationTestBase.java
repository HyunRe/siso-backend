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
 * 통합 테스트 기본 클래스 (인증 불필요)
 *
 * Security: TestSecurityConfig (permitAll)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@Import({
        TestSecurityConfig.class,
        TestAwsConfig.class
})
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedMySQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", SharedMySQLContainer::getUsername);
        registry.add("spring.datasource.password", SharedMySQLContainer::getPassword);
    }
}
