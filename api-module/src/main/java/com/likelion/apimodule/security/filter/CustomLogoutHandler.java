package com.likelion.apimodule.security.filter;

import com.likelion.apimodule.security.util.JwtUtil;
import com.likelion.commonmodule.exception.jwt.SecurityCustomException;
import com.likelion.commonmodule.exception.jwt.SecurityErrorCode;
import com.likelion.commonmodule.redis.util.RedisUtil;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
public class CustomLogoutHandler implements LogoutHandler {

	private final RedisUtil redisUtil;
	private final JwtUtil jwtUtil;

	@Override
	public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
		try {
			log.info("[*] Logout Filter");

			String accessToken = jwtUtil.resolveAccessToken(request);

			redisUtil.saveAsValue(
				accessToken,
				"logout",
				jwtUtil.getExpTime(accessToken),
				TimeUnit.MILLISECONDS
			);

			String email = jwtUtil.getEmail(accessToken);

			redisUtil.delete(
				email + "_refresh_token"
			);

			redisUtil.delete(
				email + "_fcm_token"
			);

		} catch (ExpiredJwtException e) {
			log.warn("[*] case : accessToken expired");
			throw new SecurityCustomException(SecurityErrorCode.TOKEN_EXPIRED);
		}
	}
}
