package org.duckdns.bidbuy.global.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.duckdns.bidbuy.global.auth.domain.RefreshTokenEntity;
import org.duckdns.bidbuy.global.auth.domain.RefreshTokenRepository;
import org.duckdns.bidbuy.global.auth.jwt.JWTUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JWTUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // OAuth2User
        OAuth2UserCustom customUserDetails = (OAuth2UserCustom) authentication.getPrincipal();

        String username = customUserDetails.getUsername();
        Long userId = customUserDetails.getUserId();

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Iterator<? extends GrantedAuthority> iterator = authorities.iterator();
        GrantedAuthority auth = iterator.next();
        String role = auth.getAuthority();

//        String token = jwtUtil.createJwt("access", username, role, 60 * 60 * 60 * 60L);
        String token = jwtUtil.createJwt("access", userId, username, role, 60 * 60 * 1000L); // 일단 1시간
        String refreshToken = jwtUtil.createJwt("refresh", userId, username, role, 86400000L);

        //Refresh 토큰 저장
        addRefreshTokenEntity(userId, username, refreshToken, 86400000L);

        // 토큰을 쿠키에 추가
//        response.addCookie(createCookie("Authorization",   token, 60 * 1000L)); // 일단 1분
//        response.addCookie(createCookie("refresh",   refreshToken, 86400000L));
        response.setHeader("Authorization", "Bearer " +token);
        response.setHeader("Set-Cookie", "refresh="+refreshToken+"; SameSite=None; Secure; HttpOnly; Path=/; Max-Age=86400");
        // CORS를 위해 노출할 헤더 설정
        response.setHeader("Access-Control-Allow-Origin", "https://bidbuy.duckdns.org");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Origin, Content-Type, Accept, Authorization, Set-Cookie, refresh");
        response.setHeader("Access-Control-Expose-Headers", "Authorization, refresh");
        log.info("Oauth 로그인 성공");
        response.sendRedirect("https://bidbuy.duckdns.org");
    }

    private Cookie createCookie(String key, String value, Long expiredMs) {

        Cookie cookie = new Cookie(key, value);
        cookie.setMaxAge(expiredMs.intValue() / 1000);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setHttpOnly(true);

        return cookie;
    }

    private void addRefreshTokenEntity(Long userId, String username, String refreshToken, Long expiredMs) {

        Date date = new Date(System.currentTimeMillis() + expiredMs);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.builder()
                .userId(userId)
                .userName(username)
                .refreshToken(refreshToken)
                .expiration(date.toString())
                .build();

        refreshTokenRepository.save(refreshTokenEntity);
    }
}
