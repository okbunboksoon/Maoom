package maoomWeb.ire.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class DitaValidationAccessServiceTest {

    private final DitaValidationAccessService service =
            new DitaValidationAccessService("admin,hkim");

    @Test
    void allowsOnlyConfiguredUsers() {
        assertThat(service.isAllowed(authenticated("admin"))).isTrue();
        assertThat(service.isAllowed(authenticated("hkim"))).isTrue();
        assertThat(service.isAllowed(authenticated("other"))).isFalse();
        assertThat(service.isAllowed(null)).isFalse();
    }

    @Test
    void comparesLoginIdsWithoutCaseOrOuterSpaces() {
        assertThat(service.isAllowed(authenticated("ADMIN"))).isTrue();
        assertThat(new DitaValidationAccessService(" admin, hkim ")
                .isAllowed(authenticated("hkim"))).isTrue();
    }

    @Test
    void wildcardAllowsEveryAuthenticatedUserButNotAnonymousInput() {
        DitaValidationAccessService wildcardService =
                new DitaValidationAccessService("*");

        assertThat(wildcardService.isAllowed(authenticated("admin"))).isTrue();
        assertThat(wildcardService.isAllowed(authenticated("hkim"))).isTrue();
        assertThat(wildcardService.isAllowed(authenticated("other"))).isTrue();
        assertThat(wildcardService.isAllowed(null)).isFalse();
    }

    private UsernamePasswordAuthenticationToken authenticated(String userId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                userId, "password", java.util.List.of());
    }
}
