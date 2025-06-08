package com.eipresso.user.config;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;

import org.apache.camel.spi.IdempotentRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestConfiguration
@Profile("test")  
public class UserServiceTestConfig {

    @Bean
    @Primary
    public HazelcastInstance hazelcastInstance() {
        HazelcastInstance mock = mock(HazelcastInstance.class);
        
        Cluster cluster = mock(Cluster.class);
        Member member = mock(Member.class);
        Set<Member> members = Set.of(member);
        when(cluster.getMembers()).thenReturn(members);
        when(mock.getCluster()).thenReturn(cluster);
        
        @SuppressWarnings("unchecked")
        IMap<Object, Object> mockMap = mock(IMap.class);
        Map<String, String> backingMap = new HashMap<>();
        
        when(mockMap.put(any(), any())).thenAnswer(invocation -> {
            String key = String.valueOf(invocation.getArgument(0));
            String value = String.valueOf(invocation.getArgument(1));
            return backingMap.put(key, value);
        });
        
        when(mockMap.get(any())).thenAnswer(invocation -> {
            String key = String.valueOf(invocation.getArgument(0));
            return backingMap.get(key);
        });
        
        when(mock.getMap(anyString())).thenReturn(mockMap);
        
        return mock;
    }

    @Bean
    @Primary
    public IdempotentRepository hazelcastIdempotentRepository() {
        IdempotentRepository mock = mock(IdempotentRepository.class);
        
        Map<String, Object> backingStore = new HashMap<>();
        
        when(mock.add(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (backingStore.containsKey(key)) {
                return false;
            }
            backingStore.put(key, true);
            return true;
        });
        
        when(mock.contains(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return backingStore.containsKey(key);
        });
        
        when(mock.remove(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return backingStore.remove(key) != null;
        });
        
        return mock;
    }
} 