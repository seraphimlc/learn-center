package com.vlp.auth.wechat;

import com.vlp.auth.dto.AuthDtos.AuthResp;
import com.vlp.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/wechat")
@RequiredArgsConstructor
public class WechatAuthController {
    private final WechatAuthService wechatAuthService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(wechatAuthService.status());
    }

    /** PC 扫码：返回 authorizeUrl + state（Redis TTL 10m）。 */
    @GetMapping({"/qr-url", "/start"})
    public ApiResponse<Map<String, String>> qrUrl() {
        return ApiResponse.ok(wechatAuthService.startQrLogin());
    }

    /**
     * 公众号网页授权 hook（可选）。需配置 WECHAT_MP_APP_ID / WECHAT_MP_APP_SECRET。
     */
    @GetMapping("/mp-url")
    public ApiResponse<Map<String, String>> mpUrl(@RequestParam(required = false) String scope) {
        return ApiResponse.ok(wechatAuthService.startMpAuthorize(scope));
    }

    /** 前端回调页带着 code/state 调此接口换 JWT。 */
    @GetMapping("/callback")
    public ApiResponse<AuthResp> callback(@RequestParam String code,
                                          @RequestParam String state) {
        return ApiResponse.ok(wechatAuthService.handleCallback(code, state));
    }
}
