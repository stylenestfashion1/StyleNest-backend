package com.stylenest.stylenest_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
class JacksonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void localDateTime_serializesWithExplicitUtcZMarker_samePrecisionAsBefore() throws Exception {

        // 159047 microseconds = 159047000 nanoseconds, matching the
        // 6-fractional-digit style already observed live (e.g.
        // "2026-08-11T03:41:23.159047").
        LocalDateTime value = LocalDateTime.of(2026, 8, 11, 3, 41, 23, 159_047_000);

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-08-11T03:41:23.159047Z\"");
    }

    @Test
    void localDateTime_withNoFractionalSeconds_stillGetsZMarker() throws Exception {

        LocalDateTime value = LocalDateTime.of(2026, 1, 1, 0, 0, 0);

        String json = objectMapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"2026-01-01T00:00:00Z\"");
    }

    @Test
    void localDateTime_embeddedInAnObjectField_stillGetsZMarker() throws Exception {

        record Wrapper(LocalDateTime createdAt) {}

        String json = objectMapper.writeValueAsString(
                new Wrapper(LocalDateTime.of(2026, 8, 11, 3, 30, 6, 965_825_000)));

        assertThat(json).isEqualTo("{\"createdAt\":\"2026-08-11T03:30:06.965825Z\"}");
    }
}
