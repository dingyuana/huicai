package com.huicai.sme.asset.service.impl;

import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.entity.AssetDisposalEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetDisposalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetDisposalServiceImplTest {

    @Mock private AssetDisposalMapper mapper;
    @Mock private AssetCardMapper assetCardMapper;
    @InjectMocks private AssetDisposalServiceImpl service;

    private AssetDisposalEntity stubEntity() {
        AssetDisposalEntity e = new AssetDisposalEntity();
        e.setId(1L);
        e.setDisposalNo("DISPOSAL-001");
        e.setAssetId(1L);
        e.setDisposalType("SALE");
        e.setDisposalDate(java.time.LocalDate.now());
        e.setStatus("PENDING_APPROVAL");
        e.setPeriod("202607");
        return e;
    }

    @Test
    void getById_存在_返回Entity() {
        when(mapper.selectById(1L)).thenReturn(stubEntity());
        AssetDisposalEntity result = service.getById(1L);
        assertNotNull(result);
    }

    @Test
    void getById_不存在_抛BusinessException() {
        when(mapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    void pageQuery_调selectPage() {
        when(mapper.selectPage(any(), any())).thenReturn(null);
        service.pageQuery("PENDING_APPROVAL", 1, 20);
        verify(mapper).selectPage(any(), any());
    }
}