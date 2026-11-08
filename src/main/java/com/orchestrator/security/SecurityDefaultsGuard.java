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

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Detects well-known development defaults at startup. Logs a WARN naming the settings (never their
 * values) and fails the startup when profile {@code prod} is active or {@code
 * orchestrator.security.strict=true}.
 */
@Slf4j
@Component
public class SecurityDefaultsGuard {

    private static final String DEFAULT_SECRET_PREFIX = "CHANGE_ME";
    private static final String DEFAULT_CREDENTIAL = "admin";

    private final Environment env;

    public SecurityDefaultsGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void check() {
        List<String> defaults = new ArrayList<>();
        if (env.getProperty("orchestrator.security.jwt.secret", "")
                .startsWith(DEFAULT_SECRET_PREFIX)) {
            defaults.add("JWT_SECRET (orchestrator.security.jwt.secret)");
        }
        if (DEFAULT_CREDENTIAL.equals(env.getProperty("orchestrator.security.admin.username"))
                && DEFAULT_CREDENTIAL.equals(
                        env.getProperty("orchestrator.security.admin.password"))) {
            defaults.add("ADMIN_USERNAME/ADMIN_PASSWORD (orchestrator.security.admin.*)");
        }
        if (DEFAULT_CREDENTIAL.equals(env.getProperty("orchestrator.manager.password"))) {
            defaults.add("MANAGER_PASSWORD (orchestrator.manager.password)");
        }
        if (defaults.isEmpty()) {
            return;
        }
        boolean strict =
                env.acceptsProfiles(Profiles.of("prod"))
                        || env.getProperty("orchestrator.security.strict", Boolean.class, false);
        String message = "Insecure default settings in use: " + String.join(", ", defaults);
        if (strict) {
            throw new IllegalStateException(message + ". Refusing to start (prod/strict mode).");
        }
        log.warn("{}. Change them before deploying (prod/strict mode refuses to start).", message);
    }
}
