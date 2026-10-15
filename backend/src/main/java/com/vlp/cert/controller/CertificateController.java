package com.vlp.cert.controller;

import com.vlp.cert.entity.Certificate;
import com.vlp.cert.service.CertificateService;
import com.vlp.common.ApiResponse;
import com.vlp.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certs")
@RequiredArgsConstructor
public class CertificateController {
    private final CertificateService certificateService;

    @GetMapping("/mine")
    public ApiResponse<List<Certificate>> mine() {
        return ApiResponse.ok(certificateService.mine(AuthContext.current().getId()));
    }

    @GetMapping("/{certNo}")
    public ApiResponse<Certificate> detail(@PathVariable String certNo) {
        return ApiResponse.ok(certificateService.byNo(certNo));
    }
}
