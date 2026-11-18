package com.aireview.service;

import com.aireview.dto.IdentityProviderRequest;
import com.aireview.dto.IdentityProviderVO;
import java.util.List;

/**
 * 身份源服务（对齐 memos 的 IdentityProviderService）。
 */
public interface IdentityProviderService {

    List<IdentityProviderVO> list();

    IdentityProviderVO get(String uid);

    IdentityProviderVO create(IdentityProviderRequest request);

    IdentityProviderVO update(String uid, IdentityProviderRequest request);

    void delete(String uid);
}
