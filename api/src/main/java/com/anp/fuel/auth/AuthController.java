package com.anp.fuel.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação", description = "Login simples (usuário único via configuração) que devolve um JWT")
public class AuthController {
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {}

    private final JwtEncoder encoder;
    private final String user, pass;
    private final long ttlMinutes;

    public AuthController(JwtEncoder encoder, @Value("${app.auth.username}") String user,
                          @Value("${app.auth.password}") String pass, @Value("${app.jwt.ttl-minutes}") long ttl) {
        this.encoder = encoder; this.user = user; this.pass = pass; this.ttlMinutes = ttl;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        if (!user.equals(req.username()) || !pass.equals(req.password()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(req.username()).issuedAt(now)
            .expiresAt(now.plus(Duration.ofMinutes(ttlMinutes))).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new TokenResponse(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(),
                                 "Bearer", ttlMinutes * 60);
    }
}
