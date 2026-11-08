/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.orchestrator.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

class SecurityDefaultsGuardTest {

    private static final String SECRET_VALUE = "CHANGE_ME_dev_secret_value";

    private final Logger logger = (Logger) LoggerFactory.getLogger(SecurityDefaultsGuard.class);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private MockEnvironment defaults() {
        return new MockEnvironment()
                .withProperty("orchestrator.security.jwt.secret", SECRET_VALUE)
                .withProperty("orchestrator.security.admin.username", "admin")
                .withProperty("orchestrator.security.admin.password", "admin")
                .withProperty("orchestrator.manager.password", "admin");
    }

    @Test
    @DisplayName("warns naming each default setting without printing values")
    void warnsInNormalProfile() {
        new SecurityDefaultsGuard(defaults()).check();

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("JWT_SECRET", "ADMIN_PASSWORD", "MANAGER_PASSWORD")
                .doesNotContain(SECRET_VALUE);
    }

    @Test
    @DisplayName("fails startup in strict mode")
    void failsWhenStrict() {
        MockEnvironment env = defaults().withProperty("orchestrator.security.strict", "true");

        assertThatThrownBy(() -> new SecurityDefaultsGuard(env).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining(SECRET_VALUE);
    }

    @Test
    @DisplayName("fails startup with profile prod")
    void failsWhenProd() {
        MockEnvironment env = defaults();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SecurityDefaultsGuard(env).check())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("passes silently when nothing uses defaults, even in strict mode")
    void passesWithRealSecrets() {
        MockEnvironment env =
                new MockEnvironment()
                        .withProperty("orchestrator.security.strict", "true")
                        .withProperty("orchestrator.security.jwt.secret", "real-secret")
                        .withProperty("orchestrator.security.admin.username", "ops")
                        .withProperty("orchestrator.security.admin.password", "pw")
                        .withProperty("orchestrator.manager.password", "pw");

        assertThatCode(() -> new SecurityDefaultsGuard(env).check()).doesNotThrowAnyException();
        assertThat(appender.list).isEmpty();
    }
}
