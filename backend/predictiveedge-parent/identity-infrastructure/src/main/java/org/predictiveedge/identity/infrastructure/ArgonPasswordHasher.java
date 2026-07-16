package org.predictiveedge.identity.infrastructure;

import org.predictiveedge.identity.application.IdentityPorts.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

final class ArgonPasswordHasher implements PasswordHasher {
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}
