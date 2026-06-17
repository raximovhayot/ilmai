package org.aiincubator.ilmai.auth.security;

import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

public class CurrentUserAuthentication extends AbstractAuthenticationToken {

    private final CurrentUser principal;
    private final Jwt token;

    public CurrentUserAuthentication(CurrentUser principal,
                                     Jwt token,
                                     Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public Jwt getToken() {
        return token;
    }

    @Override
    public String getName() {
        return principal.getUserId().toString();
    }
}
