package com.aireview.service.impl;

import com.aireview.common.BusinessException;
import com.aireview.common.ResultCode;
import com.aireview.dto.IdentityProviderRequest;
import com.aireview.dto.IdentityProviderVO;
import com.aireview.entity.IdentityProvider;
import com.aireview.mapper.IdentityProviderMapper;
import com.aireview.security.RequireRole;
import com.aireview.service.IdentityProviderService;
import com.aireview.util.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 身份源服务实现。管理操作需管理员。
 */
@Service
public class IdentityProviderServiceImpl implements IdentityProviderService {

    private final IdentityProviderMapper identityProviderMapper;

    public IdentityProviderServiceImpl(IdentityProviderMapper identityProviderMapper) {
        this.identityProviderMapper = identityProviderMapper;
    }

    @Override
    public List<IdentityProviderVO> list() {
        return identityProviderMapper.selectList(null).stream().map(this::toVO).toList();
    }

    @Override
    public IdentityProviderVO get(String uid) {
        return toVO(loadByUid(uid));
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public IdentityProviderVO create(IdentityProviderRequest request) {
        IdentityProvider idp = new IdentityProvider();
        idp.setUid(UidGenerator.generate());
        idp.setName(request.name());
        idp.setType(request.type());
        idp.setIdentifierFilter(request.identifierFilter());
        idp.setConfig(request.config());
        identityProviderMapper.insert(idp);
        return toVO(idp);
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public IdentityProviderVO update(String uid, IdentityProviderRequest request) {
        IdentityProvider idp = loadByUid(uid);
        if (request.name() != null) {
            idp.setName(request.name());
        }
        if (request.type() != null) {
            idp.setType(request.type());
        }
        if (request.identifierFilter() != null) {
            idp.setIdentifierFilter(request.identifierFilter());
        }
        if (request.config() != null) {
            idp.setConfig(request.config());
        }
        identityProviderMapper.updateById(idp);
        return toVO(idp);
    }

    @Override
    @Transactional
    @RequireRole("ROLE_ADMIN")
    public void delete(String uid) {
        IdentityProvider idp = loadByUid(uid);
        identityProviderMapper.deleteById(idp.getId());
    }

    private IdentityProvider loadByUid(String uid) {
        IdentityProvider idp = identityProviderMapper.selectOne(
            new LambdaQueryWrapper<IdentityProvider>().eq(IdentityProvider::getUid, uid));
        if (idp == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return idp;
    }

    private IdentityProviderVO toVO(IdentityProvider idp) {
        return new IdentityProviderVO(idp.getUid(), idp.getName(), idp.getType(),
            idp.getIdentifierFilter(), idp.getConfig());
    }
}
