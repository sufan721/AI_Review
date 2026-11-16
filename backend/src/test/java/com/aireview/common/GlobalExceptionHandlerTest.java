package com.aireview.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aireview.security.PermissionService;
import com.aireview.security.TokenService;
import com.aireview.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ValidationProbeController.class)
@Import(GlobalExceptionHandler.class)
class GlobalExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    /** WebConfig 会被 Web 切片加载，其 JwtInterceptor 依赖 JwtUtil/TokenService/PermissionService；本测试只校验异常映射。 */
    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private PermissionService permissionService;

    @Test
    void invalidInputUsesTheApiEnvelope() throws Exception {
        mockMvc.perform(post("/test/validation").contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unexpectedErrorsUseTheApiEnvelopeWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("服务器内部错误"));
    }
}

@RestController
class ValidationProbeController {
    @PostMapping("/test/validation")
    Result<Void> validate(@Valid @RequestBody ValidationProbe request) {
        return Result.ok(null);
    }

    @GetMapping("/test/boom")
    Result<Void> boom() {
        throw new IllegalStateException("数据库连接串泄露示例");
    }
}

record ValidationProbe(@NotBlank(message = "名称不能为空") String name) {
}
