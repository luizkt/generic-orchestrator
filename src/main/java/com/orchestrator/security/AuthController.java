package com.orchestrator.security;

import com.orchestrator.dto.LoginRequest;
import com.orchestrator.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // ATENÇÃO: autenticação simplificada para fins de demonstração.
        // Em produção, integre com banco de usuários e BCrypt.
        if (!"admin".equals(request.getUsername()) || !"admin".equals(request.getPassword())) {
            return ResponseEntity.status(401).build();
        }
        String token = jwtService.generateToken(request.getUsername());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer"));
    }
}
