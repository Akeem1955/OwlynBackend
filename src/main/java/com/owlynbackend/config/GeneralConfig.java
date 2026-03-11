package com.owlynbackend.config;

import com.owlynbackend.internal.dto.PendingAuthDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// FIX 1: Import the new Jackson 3.x Serializer (Notice there is no '2')
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import tools.jackson.databind.ObjectMapper; // Your Jackson 3.x import

@Configuration
public class GeneralConfig {

    @Bean
    public RedisTemplate<String, PendingAuthDTO> redisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        RedisTemplate<String, PendingAuthDTO> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);

        // Keys are plain Strings
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());

        // FIX 2: Use JacksonJsonRedisSerializer (without the '2')
        JacksonJsonRedisSerializer<PendingAuthDTO> serializer =
                new JacksonJsonRedisSerializer<>(objectMapper, PendingAuthDTO.class);

        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}