package com.vlp.auth.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlp.auth.dto.AuthDtos.AuthResp;
import com.vlp.auth.service.AuthService;
import com.vlp.common.BusinessException;
import com.vlp.user.entity.User;
import com.vlp.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 微信开放平台网站应用扫码登录（snsapi_login）+ 可选公众号网页授权 hook。
 * state 存 Redis TTL 10m，回调一次性消费，防 CSRF/重放。
 */
public class WechatAuthService {
    private static final String STATE_KEY_PREFIX = "vlp:wechat:oauth:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String QR_CONNECT =
            "https://open.weixin.qq.com/connect/qrconnect";
    private static final String MP_AUTHORIZE =
            "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String TOKEN_URL =
            "https://api.weixin.qq.com/sns/oauth2/access_token";
    private static final String USERINFO_URL =
            "https://api.weixin.qq.com/sns/userinfo";

    private final WechatProperties props;
    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public void requireConfigured() {
        if (!props.isConfigured()) {
            throw new BusinessException(503, "未配置微信登录");
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new HashMap<>();
        m.put("enabled", props.isConfigured());
        m.put("mpEnabled", props.isMpConfigured());
        return m;
    }

    /**
     * PC 网站应用扫码登录：返回 open.weixin.qq.com qrconnect URL + state。
     */
    public Map<String, String> startQrLogin() {
        requireConfigured();
        String state = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(STATE_KEY_PREFIX + state, "qr", STATE_TTL);
        } catch (Exception e) {
            log.error("Redis unavailable for wechat oauth state", e);
            throw new BusinessException(503, "登录服务暂不可用，请稍后重试");
        }
        String url = QR_CONNECT
                + "?appid=" + enc(props.getAppId())
                + "&redirect_uri=" + enc(props.getRedirectUri())
                + "&response_type=code"
                + "&scope=snsapi_login"
                + "&state=" + enc(state)
                + "#wechat_redirect";
        return Map.of("authorizeUrl", url, "state", state, "mode", "qrconnect");
    }

    /**
     * 公众号网页授权 hook（H5）：仅当 mpAppId/Secret 已配置时可用。
     */
    public Map<String, String> startMpAuthorize(String scope) {
        if (!props.isMpConfigured()) {
            throw new BusinessException(503, "未配置微信公众号授权");
        }
        String redirect = props.getRedirectUri();
        if (redirect == null || redirect.isBlank()) {
            throw new BusinessException(503, "未配置微信登录");
        }
        String sc = (scope == null || scope.isBlank()) ? "snsapi_userinfo" : scope;
        String state = UUID.randomUUID().toString().replace("-", "");
        try {
            redis.opsForValue().set(STATE_KEY_PREFIX + state, "mp", STATE_TTL);
        } catch (Exception e) {
            log.error("Redis unavailable for wechat mp oauth state", e);
            throw new BusinessException(503, "登录服务暂不可用，请稍后重试");
        }
        String url = MP_AUTHORIZE
                + "?appid=" + enc(props.getMpAppId())
                + "&redirect_uri=" + enc(redirect)
                + "&response_type=code"
                + "&scope=" + enc(sc)
                + "&state=" + enc(state)
                + "#wechat_redirect";
        return Map.of("authorizeUrl", url, "state", state, "mode", "mp_oauth");
    }

    @Transactional
    public AuthResp handleCallback(String code, String state) {
        requireConfigured();
        if (code == null || code.isBlank()) {
            throw new BusinessException(400, "缺少微信授权 code");
        }
        if (state == null || state.isBlank()) {
            throw new BusinessException(400, "缺少 state");
        }
        validateState(state);

        JsonNode tokenJson = exchangeCode(code);
        String openid = text(tokenJson, "openid");
        String unionid = text(tokenJson, "unionid");
        String accessToken = text(tokenJson, "access_token");
        if (openid == null || openid.isBlank()) {
            String err = text(tokenJson, "errmsg");
            throw new BusinessException(400, "微信授权失败" + (err != null ? "：" + err : ""));
        }

        String nickname = null;
        String avatar = null;
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                JsonNode info = fetchUserInfo(accessToken, openid);
                nickname = text(info, "nickname");
                avatar = text(info, "headimgurl");
            } catch (Exception e) {
                log.info("WeChat userinfo optional fetch failed: {}", e.getMessage());
            }
        }

        User user = upsertWechatUser(openid, unionid, nickname, avatar);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return authService.toAuth(user);
    }

    private void validateState(String state) {
        String key = STATE_KEY_PREFIX + state;
        try {
            String v = redis.opsForValue().get(key);
            if (v == null) {
                throw new BusinessException(400, "微信登录 state 无效或已过期，请重试");
            }
            redis.delete(key); // 一次性，防重放 / CSRF
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 不可用时拒绝回调，避免弱校验被 CSRF 利用（start 阶段本就会因 Redis 失败而 503）
            log.warn("Redis state check failed, reject callback: {}", e.getMessage());
            throw new BusinessException(503, "登录服务暂不可用，请稍后重试");
        }
    }

    private User upsertWechatUser(String openid, String unionid, String nickname, String avatar) {
        Optional<User> found = Optional.empty();
        if (unionid != null && !unionid.isBlank()) {
            found = userRepository.findByWechatUnionId(unionid);
        }
        if (found.isEmpty()) {
            found = userRepository.findByWechatOpenId(openid);
        }
        if (found.isPresent()) {
            User u = found.get();
            if (!Boolean.TRUE.equals(u.getEnabled())) {
                throw new BusinessException("账号已禁用，请联系管理员");
            }
            if (u.getWechatOpenId() == null) u.setWechatOpenId(openid);
            if (unionid != null && !unionid.isBlank() && u.getWechatUnionId() == null) {
                u.setWechatUnionId(unionid);
            }
            if (nickname != null && !nickname.isBlank()
                    && (u.getNickname() == null || u.getNickname().isBlank() || u.getNickname().startsWith("微信用户"))) {
                u.setNickname(trimNick(nickname));
            }
            if (avatar != null && !avatar.isBlank() && (u.getAvatar() == null || u.getAvatar().isBlank())) {
                u.setAvatar(avatar);
            }
            return u;
        }
        String shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "wx_" + shortId;
        while (userRepository.existsByUsername(username)) {
            shortId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            username = "wx_" + shortId;
        }
        String nick = (nickname != null && !nickname.isBlank()) ? trimNick(nickname) : ("微信用户" + shortId.substring(0, 4));
        return userRepository.save(User.builder()
                .username(username)
                .passwordHash(null)
                .nickname(nick)
                .avatar(avatar)
                .wechatOpenId(openid)
                .wechatUnionId(unionid != null && !unionid.isBlank() ? unionid : null)
                .role("USER")
                .enabled(true)
                .build());
    }

    private JsonNode exchangeCode(String code) {
        String url = TOKEN_URL
                + "?appid=" + enc(props.getAppId())
                + "&secret=" + enc(props.getAppSecret())
                + "&code=" + enc(code)
                + "&grant_type=authorization_code";
        return httpGetJson(url);
    }

    private JsonNode fetchUserInfo(String accessToken, String openid) {
        String url = USERINFO_URL
                + "?access_token=" + enc(accessToken)
                + "&openid=" + enc(openid)
                + "&lang=zh_CN";
        return httpGetJson(url);
    }

    private JsonNode httpGetJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 400) {
                throw new BusinessException(502, "微信接口请求失败");
            }
            return objectMapper.readTree(resp.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("WeChat HTTP error", e);
            throw new BusinessException(502, "微信接口暂时不可用，请稍后重试");
        }
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) return null;
        String v = n.get(field).asText();
        return v == null || v.isBlank() || "null".equals(v) ? null : v;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String trimNick(String n) {
        String t = n.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
