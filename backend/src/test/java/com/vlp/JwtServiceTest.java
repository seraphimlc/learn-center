
package com.vlp;

import com.vlp.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    @Test
    void issueAndParse() {
        JwtService jwt = new JwtService("TestJwtSecretKeyMustBeLongEnoughForHs256AlgorithmXX", 2);
        String token = jwt.issue(42L, "alice", "USER");
        Claims claims = jwt.parse(token);
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("uid", Number.class).longValue()).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }
}
