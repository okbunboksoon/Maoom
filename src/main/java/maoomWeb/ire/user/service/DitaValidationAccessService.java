package maoomWeb.ire.user.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** DITA Validation 기능을 사용할 수 있는 로그인 ID를 관리한다. */
@Service
public class DitaValidationAccessService {

    private final Set<String> allowedUserIds;
    private final boolean allowAllAuthenticatedUsers;

    public DitaValidationAccessService(
            @Value("${dita.validation.allowed-users:admin,hkim}") String allowedUsers) {
        this.allowedUserIds = Arrays.stream(allowedUsers.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allowAllAuthenticatedUsers = allowedUserIds.contains("*");
    }

    /** 현재 로그인 사용자가 DITA Validation 기능의 허용 대상인지 반환한다. */
    public boolean isAllowed(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && (allowAllAuthenticatedUsers
                        || allowedUserIds.contains(
                                authentication.getName().trim().toLowerCase(Locale.ROOT)));
    }
}
