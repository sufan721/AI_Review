package com.aireview.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟统一身份认证 IdP。真实环境由外部 IdP 完成认证后回传 subject，此处用一个可交互的
 * HTML 页演示完整流程：输入 subject → POST /api/auth/sso/login → 回跳前端 /sso-callback。
 */
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {
    private final String frontendBaseUrl;

    public SsoController(
        @Value("${ai-review.auth.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping(value = "/mock", produces = MediaType.TEXT_HTML_VALUE)
    public String mockIdp() {
        return """
            <!doctype html>
            <html lang="zh">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>模拟统一身份认证</title>
              <style>
                body { font-family: system-ui, sans-serif; max-width: 360px; margin: 96px auto; padding: 0 20px; }
                label { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
                input { padding: 8px 12px; border: 1px solid #d8dee4; border-radius: 8px; }
                button { padding: 8px 16px; border: 0; border-radius: 8px; background: #2f6feb; color: #fff; cursor: pointer; }
              </style>
            </head>
            <body>
              <h2>模拟统一身份认证</h2>
              <p>真实部署时由外部 IdP 认证后回传 subject，这里用表单模拟。</p>
              <form id="form">
                <label>subject（IdP 唯一标识）<input name="subject" value="demo-user-1001" required></label>
                <label>用户名（可选）<input name="username" value="sso_demo"></label>
                <label>邮箱（可选）<input name="email" value="demo@example.com"></label>
                <button type="submit">登录</button>
              </form>
              <script>
                document.getElementById('form').addEventListener('submit', async function (event) {
                  event.preventDefault();
                  const data = Object.fromEntries(new FormData(event.target));
                  const response = await fetch('/api/auth/sso/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                      provider: 'OIDC',
                      subject: data.subject,
                      username: data.username,
                      email: data.email,
                    }),
                  });
                  const body = await response.json();
                  if (body.code === 0) {
                    const d = body.data;
                    const url = '__FRONTEND_BASE__/sso-callback?accessToken='
                      + encodeURIComponent(d.accessToken)
                      + '&refreshToken=' + encodeURIComponent(d.refreshToken);
                    location.href = url;
                  } else {
                    alert(body.message);
                  }
                });
              </script>
            </body>
            </html>
            """.replace("__FRONTEND_BASE__", frontendBaseUrl);
    }
}
