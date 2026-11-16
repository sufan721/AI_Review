package com.aireview.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserVO(Long id, String username, String nickname, List<String> roles, LocalDateTime createdAt) {
}
