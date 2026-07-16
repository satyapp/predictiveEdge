package org.predictiveedge.identity.api;

import java.util.List;
import java.util.UUID;

import org.predictiveedge.identity.application.IdentityService;
import org.predictiveedge.identity.application.IdentityService.AuthenticatedIdentity;
import org.predictiveedge.identity.application.IdentityService.LoginCommand;
import org.predictiveedge.identity.application.IdentityService.RegisterCommand;
import org.predictiveedge.identity.domain.OtpChannel;
import org.predictiveedge.identity.domain.UserAccount;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/identity/v1")
public class IdentityController {
    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        var result = identityService.register(new RegisterCommand(
                request.displayName(), request.email(), request.mobileNumber(), request.password()));
        return ResponseEntity.accepted().body(new RegistrationResponse(
                "Registration accepted. Verify both channels to activate your account.",
                result.verificationSessionId(),
                List.of("EMAIL", "MOBILE"),
                result.maskedEmail(),
                result.maskedMobileNumber(),
                result.developmentMobileOtp(),
                result.expiresInSeconds(),
                result.deliveryWarning()));
    }

    @PostMapping("/auth/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody OtpVerificationRequest request) {
        identityService.verifyOtp(request.verificationSessionId(), OtpChannel.EMAIL, request.otp());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/verify-mobile")
    public ResponseEntity<Void> verifyMobile(@Valid @RequestBody OtpVerificationRequest request) {
        identityService.verifyOtp(request.verificationSessionId(), OtpChannel.MOBILE, request.otp());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/verification/otp/resend")
    public ResponseEntity<ResendResponse> resend(@Valid @RequestBody OtpResendRequest request) {
        var result = identityService.resendOtp(request.verificationSessionId(), request.channel());
        return ResponseEntity.accepted().body(new ResendResponse(result.developmentOtp(), result.expiresInSeconds()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = identityService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AuthResponse(result.accessToken(), "Bearer", result.expiresIn(), result.sessionId(), UserResponse.from(result.user())));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        return UserResponse.from(identity.user());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        identityService.logout(identity);
        return ResponseEntity.noContent().build();
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String mobileNumber,
            @NotBlank @Size(min = 12, max = 1024) String password) {}

    public record OtpVerificationRequest(
            @NotNull UUID verificationSessionId,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 1024) String password) {}

    public record OtpResendRequest(@NotNull UUID verificationSessionId, @NotNull OtpChannel channel) {}

    public record RegistrationResponse(
            String message,
            UUID verificationSessionId,
            List<String> requiredChannels,
            String maskedEmail,
            String maskedMobileNumber,
            String developmentMobileOtp,
            long expiresInSeconds,
            String deliveryWarning) {}

    public record ResendResponse(String developmentOtp, long expiresInSeconds) {}

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UUID sessionId,
            UserResponse user) {}

    public record UserResponse(
            UUID id,
            String email,
            String mobileNumber,
            String displayName,
            String state,
            boolean emailVerified,
            boolean mobileVerified) {
        static UserResponse from(UserAccount user) {
            return new UserResponse(user.id(), user.email(), user.mobileNumber(), user.displayName(), user.state().name(),
                    user.emailVerified(), user.mobileVerified());
        }
    }
}
