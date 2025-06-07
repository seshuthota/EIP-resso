package com.eipresso.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.server.git.uri=classpath:/shared",
    "spring.cloud.config.server.git.clone-on-start=false"
})
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
        // Basic context loading test
    }

} 