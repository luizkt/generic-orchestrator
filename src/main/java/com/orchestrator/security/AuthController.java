package com.orchestrator.security;

import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST in the {@code tokens} collection creates a new JWT — REST-shape.
 * Replaces the previous verb-based {@code /api/auth/login} path.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/tokens")
    public ResponseEntity<LoginResponse> createToken(@RequestBody LoginRequest request) {
        // Simplified authentication for demonstration purposes.
        // Production: integrate with user store + BCrypt.
        if (!"admin".equals(request.getUsername()) || !"admin".equals(request.getPassword())) {
            return ResponseEntity.status(401).build();
        }
        String token = jwtService.generateToken(request.getUsername());
        return ResponseEntity.status(201).body(new LoginResponse(token, "Bearer"));
    }
}
