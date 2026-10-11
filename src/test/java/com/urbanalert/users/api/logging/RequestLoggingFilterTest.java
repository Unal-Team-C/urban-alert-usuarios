package com.urbanalert.users.api.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsOneEntryWithMethodPathStatusAndDuration() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/42");
        request.setQueryString("secret=should-not-appear");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> response.setStatus(404));

        assertThat(appender.list).hasSize(1);
        Map<String, Object> fields = keyValues(appender.list.get(0));
        assertThat(fields).containsEntry("method", "GET")
                .containsEntry("path", "/api/v1/users/42")
                .containsEntry("status", 404)
                .containsKey("duration_ms");
        assertThat(((Number) fields.get("duration_ms")).doubleValue()).isGreaterThanOrEqualTo(0);
        assertThat(appender.list.get(0).getFormattedMessage()).doesNotContain("secret");
    }

    @Test
    void logsStatus500WhenTheChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(keyValues(appender.list.get(0)))
                .containsEntry("method", "POST")
                .containsEntry("path", "/api/v1/users")
                .containsEntry("status", 500);
    }

    private static Map<String, Object> keyValues(ILoggingEvent event) {
        assertThat(event.getKeyValuePairs()).isNotNull();
        return event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, (KeyValuePair kv) -> kv.value));
    }
}
