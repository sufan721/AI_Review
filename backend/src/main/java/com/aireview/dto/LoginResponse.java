package com.aireview.dto;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn, UserVO user) {
}
