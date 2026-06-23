package com.huicai.module.finance.service;

import com.huicai.common.util.TemplateContext;
import com.huicai.module.finance.entity.VoucherTemplateEntity;
import com.huicai.module.finance.mapper.VoucherTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TemplateMatcher 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class TemplateMatcherTest {

    @Mock
    private VoucherTemplateMapper templateMapper;

    @InjectMocks
    private TemplateMatcher matcher;

    private VoucherTemplateEntity bankStmtTemplate;

    @BeforeEach
    void setUp() {
        bankStmtTemplate = new VoucherTemplateEntity();
        bankStmtTemplate.setId(1L);
        bankStmtTemplate.setSource("BANK_STMT");
        bankStmtTemplate.setBusinessType("bank_fee");
    }

    @Test
    void match_source业务类型方向精确匹配() {
        when(templateMapper.matchByDimensions(any(), any(), any()))
                .thenReturn(bankStmtTemplate);

        TemplateContext ctx = new TemplateContext()
                .setSource("BANK_STMT")
                .setBusinessType("bank_fee")
                .setDirection("out");

        VoucherTemplateEntity result = matcher.match(ctx);
        assertNotNull(result);
        assertEquals(1L, result.getId().longValue());
        verify(templateMapper).matchByDimensions("BANK_STMT", "bank_fee", "out");
    }

    @Test
    void match_source业务类型忽略方向匹配() {
        when(templateMapper.matchByDimensions(eq("BANK_STMT"), eq("bank_fee"), any())).thenReturn(null);
        when(templateMapper.matchByDimensions(eq("BANK_STMT"), eq("bank_fee"), isNull())).thenReturn(bankStmtTemplate);

        TemplateContext ctx = new TemplateContext()
                .setSource("BANK_STMT")
                .setBusinessType("bank_fee")
                .setDirection("in");

        VoucherTemplateEntity result = matcher.match(ctx);
        assertNotNull(result);
        assertEquals(1L, result.getId().longValue());
    }

    @Test
    void match_分类降级匹配() {
        when(templateMapper.matchByDimensions(any(), any(), any())).thenReturn(null);
        when(templateMapper.selectActiveByClassification("bank_fee")).thenReturn(bankStmtTemplate);

        TemplateContext ctx = new TemplateContext()
                .setClassification("bank_fee")
                .setSource("BANK_STMT");

        VoucherTemplateEntity result = matcher.match(ctx);
        assertNotNull(result);
        assertEquals(1L, result.getId().longValue());
    }

    @Test
    void match_无匹配返回null() {
        when(templateMapper.matchByDimensions(any(), any(), any())).thenReturn(null);
        when(templateMapper.selectActiveByClassification(any())).thenReturn(null);

        TemplateContext ctx = new TemplateContext()
                .setSource("BANK_STMT")
                .setClassification("unknown");

        assertNull(matcher.match(ctx));
    }

    @Test
    void match_null上下文() {
        assertNull(matcher.match(null));
    }

    @Test
    void match_空source分类匹配() {
        when(templateMapper.selectActiveByClassification("bank_fee")).thenReturn(bankStmtTemplate);

        TemplateContext ctx = new TemplateContext().setClassification("bank_fee");
        VoucherTemplateEntity result = matcher.match(ctx);
        assertNotNull(result);
    }
}