package com.vlp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory,
                                          @Value("${vlp.cache.home-ttl-seconds:30}") long homeTtl,
                                          @Value("${vlp.cache.category-ttl-seconds:60}") long catTtl,
                                          @Value("${vlp.cache.course-list-ttl-seconds:30}") long courseListTtl,
                                          @Value("${vlp.cache.course-detail-ttl-seconds:60}") long courseDetailTtl,
                                          @Value("${vlp.cache.site-config-ttl-seconds:120}") long siteConfigTtl,
                                          @Value("${vlp.cache.news-list-ttl-seconds:30}") long newsListTtl,
                                          @Value("${vlp.cache.entity-ttl-seconds:60}") long entityTtl) {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer json = new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json))
                .prefixCacheNameWith("vlp:cache:")
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("homeAnon", base.entryTtl(Duration.ofSeconds(homeTtl)));
        configs.put("categories", base.entryTtl(Duration.ofSeconds(catTtl)));
        configs.put("banners", base.entryTtl(Duration.ofSeconds(catTtl)));
        configs.put("courseList", base.entryTtl(Duration.ofSeconds(courseListTtl)));
        configs.put("courseDetail", base.entryTtl(Duration.ofSeconds(courseDetailTtl)));
        configs.put("courseLessons", base.entryTtl(Duration.ofSeconds(courseDetailTtl)));
        configs.put("courseById", base.entryTtl(Duration.ofSeconds(entityTtl)));
        configs.put("lessonById", base.entryTtl(Duration.ofSeconds(entityTtl)));
        configs.put("siteConfig", base.entryTtl(Duration.ofSeconds(siteConfigTtl)));
        configs.put("newsList", base.entryTtl(Duration.ofSeconds(newsListTtl)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
