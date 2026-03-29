package com.example.mirad.controller;

import com.example.mirad.security.RateLimiter;
import com.example.mirad.service.UserService;
import com.example.mirad.util.ApiResponses;
import com.example.mirad.util.RequestUtils;
import com.example.mirad.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(5);
    private static final Duration REGISTER_WINDOW = Duration.ofMinutes(10);

    private final UserService userService;
    private final RateLimiter rateLimiter;

    public AuthController(UserService userService, RateLimiter rateLimiter) {
        this.userService = userService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public Map<String, Object> register(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request
    ) throws IOException {
        String clientIp = RequestUtils.clientIp(request);
        if (!rateLimiter.allow("register", clientIp, 5, REGISTER_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many registration attempts, please retry later");
        }

        userService.register(username, password);
        return ApiResponses.success("Registration completed, please sign in");
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpServletRequest request
    ) throws IOException {
        String clientIp = RequestUtils.clientIp(request);
        if (!rateLimiter.allow("login", clientIp, 20, LOGIN_WINDOW)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts, please retry later");
        }

        if (!userService.authenticate(username, password)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("username", username.trim());
        return ApiResponses.loginState(true, username.trim());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponses.success("Signed out");
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        String username = RequestUtils.sessionUsername(request);
        if (username == null) {
            return ApiResponses.loginState(false, null);
        }
        return ApiResponses.loginState(true, username);
    }
}
