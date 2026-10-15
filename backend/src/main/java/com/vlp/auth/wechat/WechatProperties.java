package com.vlp.auth.wechat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WeChat Open Platform (网站应用) + optional Official Account (公众号) hooks.
 * Env: WECHAT_APP_ID / WECHAT_APP_SECRET / WECHAT_REDIRECT_URI / WECHAT_ENABLED
 * Optional MP: WECHAT_MP_APP_ID / WECHAT_MP_APP_SECRET
 */
@Data
@Component
@ConfigurationProperties(prefix = "vlp.wechat")
public class WechatProperties {
    /** Master switch; also requires app-id + app-secret to be considered enabled. */
    private boolean enabled = false;
    /** Open Platform website app AppId. */
    private String appId = "";
    /** Open Platform website app AppSecret. */
    private String appSecret = "";
    /** OAuth callback, e.g. https://learn.visitworld.me/login/wechat/callback */
    private String redirectUri = "";
    /** Optional 公众号 AppId for H5 snsapi_userinfo authorize (hook only). */
    private String mpAppId = "";
    /** Optional 公众号 AppSecret. */
    private String mpAppSecret = "";

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    public boolean isMpConfigured() {
        return mpAppId != null && !mpAppId.isBlank()
                && mpAppSecret != null && !mpAppSecret.isBlank();
    }
}
