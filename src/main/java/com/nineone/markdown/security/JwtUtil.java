package com.nineone.markdown.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // JWT claims中的自定义字段名
    private static final String CLAIM_KEY_USER_ID = "userId";
    private static final String CLAIM_KEY_NICKNAME = "nickname";
    private static final String CLAIM_KEY_AUTHORITIES = "authorities";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        final Claims claims = extractAllClaims(token);
        return claims.get(CLAIM_KEY_USER_ID, Long.class);
    }

    /**
     * 从 JWT Token 中提取用户昵称（无需查询数据库）
     */
    public String extractNickname(String token) {
        final Claims claims = extractAllClaims(token);
        return claims.get(CLAIM_KEY_NICKNAME, String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        final Claims claims = extractAllClaims(token);
        return claims.get(CLAIM_KEY_AUTHORITIES, List.class);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * 生成JWT Token（包含用户详细信息）
     * 将 userId、nickname、authorities 等高频信息塞入 payload，
     * 后续过滤器解析 Token 时无需查询数据库即可还原用户信息
     */
    public String generateToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_KEY_USER_ID, userDetails.getId());
        claims.put(CLAIM_KEY_NICKNAME, userDetails.getNickname());
        claims.put(CLAIM_KEY_AUTHORITIES, userDetails.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList()));
        return createToken(claims, userDetails.getUsername());
    }

    /**
     * 生成JWT Token（包含用户详细信息）
     */
    public String generateToken(UserDetails userDetails) {
        if (userDetails instanceof CustomUserDetails) {
            return generateToken((CustomUserDetails) userDetails);
        }
        
        // 回退到基础版本
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证Token是否有效（不查询数据库）
     */
    public Boolean validateToken(String token) {
        return !isTokenExpired(token);
    }

    /**
     * 验证Token是否与用户匹配（兼容旧版本）
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
