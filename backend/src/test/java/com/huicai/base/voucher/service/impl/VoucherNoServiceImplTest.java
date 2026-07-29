package com.huicai.base.voucher.service.impl;

import com.huicai.base.system.entity.VoucherTypeEntity;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VoucherNoService 专用测试 — 覆盖 Redis 计数器初始化、DB 回退、异常路径
 *
 * <p>H-17 修复：生产事故中 Redis 凭证号计数器被清空，导致重复凭证号。
 * 本测试验证 generateNextNo() 在 Redis key 缺失时从数据库初始化计数器的逻辑。
 */
@ExtendWith(MockitoExtension.class)
class VoucherNoServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private VoucherTypeService voucherTypeService;

    @Mock
    private VoucherMapper voucherMapper;

    @Mock
    private ValueOperations<String, String> valueOps;

    private VoucherNoServiceImpl service;

    private static final String PERIOD = "202607";
    private static final Long VOUCHER_TYPE_ID = 1L;
    private static final String TYPE_CODE = "JZ";
    private static final String REDIS_KEY = "voucher:no:202607:1";

    @BeforeEach
    void setUp() {
        service = new VoucherNoServiceImpl(redisTemplate, voucherTypeService, voucherMapper);
    }

    private VoucherTypeEntity mockVoucherType() {
        VoucherTypeEntity type = new VoucherTypeEntity();
        type.setId(VOUCHER_TYPE_ID);
        type.setCode(TYPE_CODE);
        type.setName("记账凭证");
        return type;
    }

    // ==================== generateNextNo ====================

    @Test
    @DisplayName("generateNextNo — Redis key 存在，直接递增返回")
    void generateNextNo_redisKeyExists_success() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(REDIS_KEY)).thenReturn(1L);

        String result = service.generateNextNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070001", result);
        verify(voucherMapper, never()).selectMaxVoucherNo(anyString(), anyLong());
    }

    @Test
    @DisplayName("generateNextNo — Redis key 缺失，从 DB 初始化后递增返回")
    void generateNextNo_redisKeyMissing_initFromDb() {
        VoucherTypeEntity type = mockVoucherType();
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(type);
        // Redis key 不存在
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        // DB 有最大凭证号 JZ2026070005
        when(voucherMapper.selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID)).thenReturn("JZ2026070005");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(REDIS_KEY, "5")).thenReturn(true);
        when(valueOps.increment(REDIS_KEY)).thenReturn(6L);

        String result = service.generateNextNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070006", result);
        verify(voucherMapper).selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID);
        verify(valueOps).setIfAbsent(REDIS_KEY, "5");
    }

    @Test
    @DisplayName("generateNextNo — Redis key 缺失，DB 无记录，从 1 开始")
    void generateNextNo_redisKeyMissing_dbNoRecord() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        // DB 无记录
        when(voucherMapper.selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(REDIS_KEY)).thenReturn(1L);

        String result = service.generateNextNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070001", result);
        // DB 无记录时不调用 setIfAbsent
        verify(valueOps, never()).setIfAbsent(anyString(), anyString());
    }

    @Test
    @DisplayName("generateNextNo — 凭证类型不存在抛 BusinessException")
    void generateNextNo_typeNotFound_throws() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.generateNextNo(PERIOD, VOUCHER_TYPE_ID));
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("generateNextNo — Redis INCR 返回 null 抛 BusinessException")
    void generateNextNo_incrementFails_throws() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(REDIS_KEY)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.generateNextNo(PERIOD, VOUCHER_TYPE_ID));
    }

    @Test
    @DisplayName("generateNextNo — DB 最大凭证号格式异常，不初始化计数器")
    void generateNextNo_dbMaxNoMalformed_skipInit() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.hasKey(REDIS_KEY)).thenReturn(false);
        // DB 最大凭证号格式异常（不匹配类型代码前缀）
        when(voucherMapper.selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID)).thenReturn("SK2026079999");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(REDIS_KEY)).thenReturn(1L);

        String result = service.generateNextNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070001", result);
        // 格式异常时不调用 setIfAbsent
        verify(valueOps, never()).setIfAbsent(anyString(), anyString());
    }

    // ==================== getCurrentNo ====================

    @Test
    @DisplayName("getCurrentNo — Redis 有值，直接返回")
    void getCurrentNo_redisHasValue_success() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(REDIS_KEY)).thenReturn("5");

        String result = service.getCurrentNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070005", result);
        verify(voucherMapper, never()).selectMaxVoucherNo(anyString(), anyLong());
    }

    @Test
    @DisplayName("getCurrentNo — Redis 无值，从 DB 回退")
    void getCurrentNo_fallbackToDb() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(REDIS_KEY)).thenReturn(null);
        when(voucherMapper.selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID)).thenReturn("JZ2026070003");

        String result = service.getCurrentNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070003", result);
    }

    @Test
    @DisplayName("getCurrentNo — Redis 和 DB 均无记录，返回 0 编号")
    void getCurrentNo_noHistory_returnsZero() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(mockVoucherType());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(REDIS_KEY)).thenReturn(null);
        when(voucherMapper.selectMaxVoucherNo(PERIOD, VOUCHER_TYPE_ID)).thenReturn(null);

        String result = service.getCurrentNo(PERIOD, VOUCHER_TYPE_ID);

        assertEquals("JZ2026070000", result);
    }

    @Test
    @DisplayName("getCurrentNo — 凭证类型不存在抛 BusinessException")
    void getCurrentNo_typeNotFound_throws() {
        when(voucherTypeService.getById(VOUCHER_TYPE_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.getCurrentNo(PERIOD, VOUCHER_TYPE_ID));
    }
}