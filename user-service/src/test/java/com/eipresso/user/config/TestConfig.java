package com.eipresso.user.config;

import com.hazelcast.map.IMap;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public MemoryIdempotentRepository hazelcastIdempotentRepository() {
        return new MemoryIdempotentRepository();
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public IMap<String, String> jwtBlacklistMap() {
        return Mockito.mock(IMap.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public IMap<String, Object> userSessionMap() {
        return Mockito.mock(IMap.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public IMap<String, Object> auditCacheMap() {
        return Mockito.mock(IMap.class);
    }

    @Bean
    @Primary
    public ProducerTemplate producerTemplate() {
        return Mockito.mock(ProducerTemplate.class);
    }
} 