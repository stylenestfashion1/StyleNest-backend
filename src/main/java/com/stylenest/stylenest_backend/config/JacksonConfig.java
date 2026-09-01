package com.stylenest.stylenest_backend.config;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@Configuration
public class JacksonConfig {

    /**
     * Every LocalDateTime field returned to clients (createdAt, updatedAt,
     * ...) is populated from Hibernate's @CreationTimestamp/@UpdateTimestamp
     * and is, in practice, always UTC -- but LocalDateTime carries no zone
     * info, so Jackson's default serialization omits any marker, leaving
     * the value ambiguous to any client. The instant itself is correct;
     * only the missing explicit marker is the gap. Since it genuinely is
     * UTC, mark it as such instead of changing what's stored/returned.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {

        return builder -> builder.serializerByType(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {

            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {

                gen.writeString(DateTimeFormatter.ISO_INSTANT.format(value.toInstant(ZoneOffset.UTC)));
            }
        });
    }
}
