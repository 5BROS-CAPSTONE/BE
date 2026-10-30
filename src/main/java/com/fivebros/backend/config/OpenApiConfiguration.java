package com.fivebros.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "5BROS Backend API",
                        version = "v1",
                        description =
                                "API quản lý tài khoản và hỗ trợ vận hành drone. "
                                        + "Đăng nhập để lấy accessToken, sau đó dán token vào Authorize. "
                                        + "Hệ thống nội bộ: Admin tạo tài khoản kèm mật khẩu và role, rồi cung cấp thông tin đăng nhập."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Dán giá trị accessToken từ API đăng nhập, không thêm tiền tố Bearer.")
public class OpenApiConfiguration {}
