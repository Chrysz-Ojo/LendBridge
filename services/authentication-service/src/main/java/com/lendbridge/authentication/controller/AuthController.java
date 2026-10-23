package com.lendbridge.authentication.controller;

import com.lendbridge.authentication.dto.SmsVerifyRequest;
import com.lendbridge.authentication.dto.TotpVerifyRequest;
import com.lendbridge.authentication.dto.TotpEnrollResponse;
import com.lendbridge.authentication.service.SmsMfaService;
import com.lendbridge.authentication.service.TotpService;
import com.lendbridge.authentication.model.MfaMethod;
import com.lendbridge.authentication.dto.LoginRequest;
import com.lendbridge.authentication.dto.LogoutRequest;
import com.lendbridge.authentication.dto.MfaLoginRequest;
import com.lendbridge.authentication.dto.RefreshRequest;
import com.lendbridge.authentication.dto.LoginResponse;
import com.lendbridge.authentication.dto.TokenResponse;
import com.lendbridge.authentication.service.LoginService;
import com.lendbridge.authentication.dto.PasswordResetConfirmRequest;
import com.lendbridge.authentication.dto.PasswordResetRequest;
import com.lendbridge.authentication.service.PasswordResetService;
import com.lendbridge.authentication.dto.RoleAssignmentRequest;
import com.lendbridge.authentication.dto.RoleListResponse;
import com.lendbridge.authentication.service.RoleAssignmentService;
import com.lendbridge.authentication.dto.RegisterRequest;
import com.lendbridge.authentication.dto.VerifyEmailRequest;
import com.lendbridge.authentication.dto.VerifyPhoneRequest;
import com.lendbridge.authentication.dto.RegisterResponse;
import com.lendbridge.authentication.service.RegistrationService;
import com.lendbridge.authentication.security.CurrentUser;
import com.lendbridge.authentication.security.JwtKeyProvider;
import com.lendbridge.authentication.exception.NotFoundException;
import com.lendbridge.authentication.dto.ApiResponse;
import com.lendbridge.authentication.dto.MessageResponse;
import com.lendbridge.authentication.dto.UserResponse;
import com.lendbridge.authentication.mapper.UserMapper;
import com.lendbridge.authentication.model.User;
import com.lendbridge.authentication.repository.UserJpaRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Single entry point for every Authentication Service endpoint. Each feature keeps its own
 * application-layer service (and its own request/response DTOs under its own {@code api.dto}
 * package); only the controller layer is consolidated here.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final RegistrationService registrationService;
  private final LoginService loginService;
  private final TotpService totpService;
  private final SmsMfaService smsMfaService;
  private final PasswordResetService passwordResetService;
  private final RoleAssignmentService roleAssignmentService;
  private final UserJpaRepository userRepository;
  private final UserMapper userMapper;
  private final JwtKeyProvider jwtKeyProvider;

  // ---- registration ----

  @PostMapping("/api/v1/auth/register")
  public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
    RegisterResponse response = registrationService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/register/verify-phone")
  public ResponseEntity<ApiResponse<MessageResponse>> verifyPhone(
      @Valid @RequestBody VerifyPhoneRequest request) {
    registrationService.verifyPhone(request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("Phone number verified")));
  }

  @PostMapping("/api/v1/auth/register/verify-email")
  public ResponseEntity<ApiResponse<MessageResponse>> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request) {
    registrationService.verifyEmail(request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("Email verified")));
  }

  // ---- login / sessions ----

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<ApiResponse<LoginResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    LoginResponse response =
        loginService.login(request, clientIp(httpRequest), deviceInfo(httpRequest));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/login/mfa")
  public ResponseEntity<ApiResponse<TokenResponse>> completeMfa(
      @Valid @RequestBody MfaLoginRequest request, HttpServletRequest httpRequest) {
    TokenResponse response =
        loginService.completeMfa(request, clientIp(httpRequest), deviceInfo(httpRequest));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/refresh")
  public ResponseEntity<ApiResponse<TokenResponse>> refresh(
      @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
    TokenResponse response =
        loginService.refresh(request, clientIp(httpRequest), deviceInfo(httpRequest));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/logout")
  public ResponseEntity<ApiResponse<MessageResponse>> logout(@Valid @RequestBody LogoutRequest request) {
    loginService.logout(request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("Logged out")));
  }

  // ---- MFA ----

  @PostMapping("/api/v1/auth/mfa/totp/enroll")
  public ResponseEntity<ApiResponse<TotpEnrollResponse>> enrollTotp(Authentication authentication) {
    TotpEnrollResponse response = totpService.enroll(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/mfa/totp/verify")
  public ResponseEntity<ApiResponse<MessageResponse>> verifyTotp(
      @Valid @RequestBody TotpVerifyRequest request, Authentication authentication) {
    totpService.verifyAndActivate(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("TOTP MFA activated")));
  }

  @PostMapping("/api/v1/auth/mfa/sms/challenge")
  public ResponseEntity<ApiResponse<MessageResponse>> challengeSms(Authentication authentication) {
    smsMfaService.challenge(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("SMS code sent")));
  }

  @PostMapping("/api/v1/auth/mfa/sms/verify")
  public ResponseEntity<ApiResponse<MessageResponse>> verifySms(
      @Valid @RequestBody SmsVerifyRequest request, Authentication authentication) {
    smsMfaService.verify(CurrentUser.idFrom(authentication), request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("SMS MFA activated")));
  }

  @DeleteMapping("/api/v1/auth/mfa/{method}")
  public ResponseEntity<ApiResponse<MessageResponse>> disableMfa(
      @PathVariable MfaMethod method, Authentication authentication) {
    if (method == MfaMethod.TOTP) {
      totpService.disable(CurrentUser.idFrom(authentication));
    } else {
      smsMfaService.disable(CurrentUser.idFrom(authentication));
    }
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of(method + " MFA disabled")));
  }

  // ---- password reset ----

  @PostMapping("/api/v1/auth/password-reset/request")
  public ResponseEntity<ApiResponse<MessageResponse>> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.request(request);
    return ResponseEntity.ok(
        ApiResponse.success(
            MessageResponse.of("If an account exists, a password reset link has been sent")));
  }

  @PostMapping("/api/v1/auth/password-reset/confirm")
  public ResponseEntity<ApiResponse<MessageResponse>> confirmPasswordReset(
      @Valid @RequestBody PasswordResetConfirmRequest request) {
    passwordResetService.confirm(request);
    return ResponseEntity.ok(ApiResponse.success(MessageResponse.of("Password has been reset")));
  }

  // ---- current user ----

  @GetMapping("/api/v1/auth/me")
  public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
    UUID userId = CurrentUser.idFrom(authentication);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    List<String> roles = roleAssignmentService.rolesOf(userId).stream().map(Enum::name).toList();
    return ResponseEntity.ok(ApiResponse.success(userMapper.toResponse(user, roles)));
  }

  // ---- RBAC (admin) ----

  @GetMapping("/api/v1/auth/admin/users/{id}/roles")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<RoleListResponse>> listRoles(@PathVariable("id") UUID userId) {
    RoleListResponse response = new RoleListResponse(roleAssignmentService.rolesOf(userId));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/api/v1/auth/admin/users/{id}/roles")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<RoleListResponse>> assignRole(
      @PathVariable("id") UUID userId,
      @Valid @RequestBody RoleAssignmentRequest request,
      Authentication authentication) {
    roleAssignmentService.assign(userId, request, CurrentUser.idFrom(authentication));
    RoleListResponse response = new RoleListResponse(roleAssignmentService.rolesOf(userId));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  // ---- JWKS ----
  // Intentionally NOT wrapped in ApiResponse: this is a standardized (RFC 7517) well-known
  // endpoint consumed by generic JWKS clients/libraries, not an application-specific API response.

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    RSAKey jwk =
        new RSAKey.Builder(jwtKeyProvider.getPublicKey())
            .keyID(jwtKeyProvider.getKeyId())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
    return Map.of("keys", List.of(jwk.toPublicJWK().toJSONObject()));
  }

  private String deviceInfo(HttpServletRequest request) {
    return request.getHeader("User-Agent");
  }

  private String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
