package com.docgen.service;

import com.docgen.config.JwtProperties;
import com.docgen.config.RedisConfig;
import com.docgen.dto.*;
import com.docgen.entity.User;
import com.docgen.exception.BusinessException;
import com.docgen.exception.ErrorCode;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Service handling user registration, authentication, token management and profile updates.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    // Password must contain: uppercase, lowercase, digit, special character
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RedisTemplate<String, String> redisTemplate;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties,
                       RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Register a new user under the given tenant.
     */
    @Transactional
    public UserDTO register(RegisterRequest request, Long tenantId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "用户名已存在", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "邮箱已被注册", HttpStatus.CONFLICT);
        }

        validatePasswordStrength(request.getPassword());

        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");

        User saved = userRepository.save(user);
        log.info("User registered: username={}, tenantId={}", saved.getUsername(), tenantId);
        return toDTO(saved);
    }

    /**
     * Authenticate user and return a JWT token pair.
     */
    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                        "用户名或密码错误", HttpStatus.UNAUTHORIZED));

        // Check if account is locked
        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "账户已锁定，请稍后再试", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleLoginFailure(user);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }

        // Reset fail count on successful login
        if (user.getLoginFailCount() > 0) {
            user.setLoginFailCount(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return generateTokenPair(user);
    }

    /**
     * Refresh the access token using a valid refresh token.
     */
    @Transactional(readOnly = true)
    public TokenPair refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN,
                    "无效的刷新令牌", HttpStatus.UNAUTHORIZED);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String storedToken = RedisConfig.getRefreshToken(redisTemplate, userId);

        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN,
                    "刷新令牌已失效", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_TOKEN,
                        "用户不存在", HttpStatus.UNAUTHORIZED));

        return generateTokenPair(user);
    }

    /**
     * Request a password reset email (placeholder implementation).
     */
    public void resetPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            // TODO: Generate reset token, store in Redis with 30-min TTL, send email
            log.info("Password reset requested for email={}", email);
        });
        // Always return success to prevent email enumeration
    }

    /**
     * Update user profile fields.
     */
    @Transactional
    public UserDTO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "用户不存在", HttpStatus.NOT_FOUND));

        // UpdateProfileRequest has nickname, avatarUrl, contactInfo
        // The users table doesn't have these columns yet, so we log and return current state.
        // These fields can be added in a future migration.
        log.info("Profile update requested for userId={}", userId);
        return toDTO(user);
    }

    /**
     * Get a user by ID.
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "用户不存在", HttpStatus.NOT_FOUND));
        return toDTO(user);
    }

    /**
     * List users with pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toDTO);
    }

    /**
     * Tenant admin or super admin: update role, team assignment, and maker-checker lane.
     */
    @Transactional
    public UserDTO updateUserAdmin(Long userId, AdminUserUpdateRequest request, UserPrincipal principal) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "User not found", HttpStatus.NOT_FOUND));
        assertAdminMayEditUser(principal, user);

        Long oldTeamId = user.getTeamId();
        user.setRole(request.getRole());
        user.setTeamId(request.getTeamId());

        if (request.getTeamId() == null) {
            user.setTeamReviewLane(null);
        } else {
            if (request.getTeamReviewLane() == null) {
                if (!Objects.equals(oldTeamId, request.getTeamId())) {
                    user.setTeamReviewLane(null);
                }
            } else {
                String lane = request.getTeamReviewLane().trim();
                if (lane.isEmpty()) {
                    user.setTeamReviewLane(null);
                } else if (!"MAKER".equals(lane) && !"CHECKER".equals(lane)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "teamReviewLane must be MAKER or CHECKER", HttpStatus.BAD_REQUEST);
                } else {
                    user.setTeamReviewLane(lane);
                }
            }
        }

        User saved = userRepository.save(user);
        log.info("User admin update: userId={} by principalUserId={}", userId, principal.getUserId());
        return toDTO(saved);
    }

    private void assertAdminMayEditUser(UserPrincipal principal, User user) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN,
                    "Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        if ("SUPER_ADMIN".equals(principal.getRole())) {
            return;
        }
        if ("TENANT_ADMIN".equals(principal.getRole())
                && principal.getTenantId().equals(user.getTenantId())) {
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                "Not allowed to manage this user", HttpStatus.FORBIDDEN);
    }

    /**
     * Delete a user by ID.
     */
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "用户不存在", HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(userId);
        RedisConfig.deleteRefreshToken(redisTemplate, userId);
        log.info("User deleted: userId={}", userId);
    }


    /**
     * Validate that the password meets strength requirements:
     * ≥8 chars, uppercase, lowercase, digit, special character.
     */
    public void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码长度不能少于8位", HttpStatus.BAD_REQUEST);
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码必须包含大写字母", HttpStatus.BAD_REQUEST);
        }
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码必须包含小写字母", HttpStatus.BAD_REQUEST);
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码必须包含数字", HttpStatus.BAD_REQUEST);
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "密码必须包含特殊字符", HttpStatus.BAD_REQUEST);
        }
    }


    private void handleLoginFailure(User user) {
        int newFailCount = user.getLoginFailCount() + 1;
        user.setLoginFailCount(newFailCount);

        if (newFailCount >= MAX_LOGIN_FAILURES) {
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
            log.warn("Account locked due to {} failed login attempts: username={}",
                    newFailCount, user.getUsername());
        }

        userRepository.save(user);
    }

    private TokenPair generateTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getTenantId(), user.getRole(), user.getTeamId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RedisConfig.storeRefreshToken(redisTemplate, user.getId(),
                refreshToken, jwtProperties.getRefreshTokenExpiration());

        return new TokenPair(accessToken, refreshToken);
    }

    public UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getTeamId(),
                user.getLanguagePreference(),
                user.getCreatedAt());
        dto.setTeamReviewLane(user.getTeamReviewLane());
        return dto;
    }
}

