package com.docgen.service;

import com.docgen.config.JwtProperties;
import com.docgen.dto.*;
import com.docgen.entity.Team;
import com.docgen.entity.TeamApprovalMode;
import com.docgen.entity.User;
import com.docgen.exception.BusinessException;
import com.docgen.repository.TeamRepository;
import com.docgen.repository.UserRepository;
import com.docgen.util.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, teamRepository, passwordEncoder,
                jwtTokenProvider, jwtProperties, redisTemplate);
    }


    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "Pass1234!");
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            u.setCreatedAt(Instant.now());
            return u;
        });

        UserDTO result = userService.register(request, 10L);

        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
        assertEquals(10L, result.getTenantId());
        assertEquals("USER", result.getRole());
    }

    @Test
    void register_duplicateUsername_throws() {
        RegisterRequest request = new RegisterRequest("existing", "new@example.com", "Pass1234!");
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, 1L));
        assertEquals("Username already exists", ex.getMessage());
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("newuser", "existing@example.com", "Pass1234!");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(request, 1L));
        assertEquals("Email is already registered", ex.getMessage());
    }


    @Test
    void validatePasswordStrength_tooShort_throws() {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength("Ab1!"));
    }

    @Test
    void validatePasswordStrength_noUppercase_throws() {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength("abcdefg1!"));
    }

    @Test
    void validatePasswordStrength_noLowercase_throws() {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength("ABCDEFG1!"));
    }

    @Test
    void validatePasswordStrength_noDigit_throws() {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength("Abcdefgh!"));
    }

    @Test
    void validatePasswordStrength_noSpecialChar_throws() {
        assertThrows(BusinessException.class,
                () -> userService.validatePasswordStrength("Abcdefg1"));
    }

    @Test
    void validatePasswordStrength_valid_passes() {
        assertDoesNotThrow(() -> userService.validatePasswordStrength("Abcdefg1!"));
    }


    @Test
    void login_success() {
        User user = createTestUser();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass1234!", "$2a$hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(1L, 10L, "USER", null)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TokenPair result = userService.login(new LoginRequest("testuser", "Pass1234!"));

        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
    }

    @Test
    void login_wrongPassword_incrementsFailCount() {
        User user = createTestUser();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("testuser", "wrong")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getLoginFailCount());
    }

    @Test
    void login_lockedAccount_throws() {
        User user = createTestUser();
        user.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("testuser", "Pass1234!")));
        assertEquals("AUTH_ACCOUNT_LOCKED", ex.getErrorCode());
    }

    @Test
    void login_fiveFailures_locksAccount() {
        User user = createTestUser();
        user.setLoginFailCount(4); // next failure will be the 5th
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> userService.login(new LoginRequest("testuser", "wrong")));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getLoginFailCount());
        assertNotNull(captor.getValue().getLockedUntil());
    }


    @Test
    void refreshToken_success() {
        when(jwtTokenProvider.validateToken("valid-refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("valid-refresh")).thenReturn(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("refresh_token:1")).thenReturn("valid-refresh");

        User user = createTestUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(1L, 10L, "USER", null)).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new-refresh");
        when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);

        TokenPair result = userService.refreshToken("valid-refresh");

        assertEquals("new-access", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());
    }

    @Test
    void refreshToken_invalidToken_throws() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> userService.refreshToken("bad-token"));
    }


    @Test
    void deleteUser_success() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_notFound_throws() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThrows(BusinessException.class, () -> userService.deleteUser(99L));
    }


    @Test
    void updateUserAdmin_makerCheckerTeam_requiresLane() {
        User user = createTestUser();
        user.setTeamId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(10L);
        team.setApprovalMode(TeamApprovalMode.MAKER_CHECKER);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("USER");
        req.setTeamId(5L);
        req.setTeamReviewLane(null);

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        assertThrows(BusinessException.class, () -> userService.updateUserAdmin(1L, req, principal));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserAdmin_makerCheckerTeam_withMakerLane_saves() {
        User user = createTestUser();
        user.setTeamId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(10L);
        team.setApprovalMode(TeamApprovalMode.MAKER_CHECKER);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("USER");
        req.setTeamId(5L);
        req.setTeamReviewLane("MAKER");

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        UserDTO dto = userService.updateUserAdmin(1L, req, principal);
        assertEquals("MAKER", dto.getTeamReviewLane());
        verify(userRepository).save(any());
    }

    @Test
    void updateUserAdmin_crossReviewTeam_nullLane_saves() {
        User user = createTestUser();
        user.setTeamId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(10L);
        team.setApprovalMode(TeamApprovalMode.CROSS_REVIEW);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("USER");
        req.setTeamId(5L);
        req.setTeamReviewLane(null);

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        UserDTO dto = userService.updateUserAdmin(1L, req, principal);
        assertNull(dto.getTeamReviewLane());
        verify(userRepository).save(any());
    }

    @Test
    void updateUserAdmin_teamWrongTenant_throws() {
        User user = createTestUser();
        user.setTeamId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(99L);
        team.setApprovalMode(TeamApprovalMode.CROSS_REVIEW);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("USER");
        req.setTeamId(5L);

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        assertThrows(BusinessException.class, () -> userService.updateUserAdmin(1L, req, principal));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserAdmin_teamNotFound_throws() {
        User user = createTestUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(teamRepository.findById(5L)).thenReturn(Optional.empty());

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("USER");
        req.setTeamId(5L);
        req.setTeamReviewLane("MAKER");

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        assertThrows(BusinessException.class, () -> userService.updateUserAdmin(1L, req, principal));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUserAdmin_preservesLaneWhenTeamUnchangedAndLaneOmitted() {
        User user = createTestUser();
        user.setTeamId(5L);
        user.setTeamReviewLane("MAKER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Team team = new Team();
        team.setId(5L);
        team.setTenantId(10L);
        team.setApprovalMode(TeamApprovalMode.MAKER_CHECKER);
        when(teamRepository.findById(5L)).thenReturn(Optional.of(team));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setRole("TEAM_ADMIN");
        req.setTeamId(5L);
        req.setTeamReviewLane(null);

        UserPrincipal principal = new UserPrincipal(2L, 10L, "TENANT_ADMIN", null, "admin");

        UserDTO dto = userService.updateUserAdmin(1L, req, principal);
        assertEquals("MAKER", dto.getTeamReviewLane());
        verify(userRepository).save(any());
    }


    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setTenantId(10L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$hashed");
        user.setRole("USER");
        user.setLoginFailCount(0);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }
}

