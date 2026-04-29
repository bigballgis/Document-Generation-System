package com.docgen.controller;

import com.docgen.dto.*;
import com.docgen.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestParam(required = false) Long tenantId) {
        // tenantId is required; SUPER_ADMIN assigns users to tenants
        if (tenantId == null) {
            tenantId = 1L; // default tenant for development
        }
        UserDTO user = userService.register(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokenPair = userService.login(request);
        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        TokenPair tokenPair = userService.refreshToken(refreshToken);
        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        userService.resetPassword(email);
        return ResponseEntity.ok().build();
    }
}
