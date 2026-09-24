package com.huicai.base.voucher.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.base.voucher.dto.VoucherQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class VoucherServiceImplTest {

    @Autowired
    VoucherServiceImpl voucherService;

    @MockBean
    VoucherMapper voucherMapper;

    @Test
    void pageQuery_scopeCompleted_调用时含scopeCompleted() {
        // given
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), ""), VoucherEntity.class);
        Page<VoucherEntity> page = new Page<>(1, 20, 1);
        page.setRecords(new ArrayList<>());
        when(voucherMapper.selectVoucherPage(any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        // when
        voucherService.pageQuery(voucherDto("POSTED", "completed"));

        // then — scope 应传 completed（终态 IN('POSTED')）
        verify(voucherMapper).selectVoucherPage(any(Page.class), any(), any(), any(), any(), any(), any(), any(), eq("completed"), any(), any());
    }

    @Test
    void pageQuery_scopePending_调用时含scopePending() {
        // given
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), ""), VoucherEntity.class);
        Page<VoucherEntity> page = new Page<>(1, 20, 1);
        page.setRecords(new ArrayList<>());
        when(voucherMapper.selectVoucherPage(any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        // when — scope=pending 排除终态
        voucherService.pageQuery(voucherDto("DRAFT", "pending"));

        // then — scope 应传 pending（终态 NOT IN）
        verify(voucherMapper).selectVoucherPage(any(Page.class), any(), any(), any(), any(), any(), any(), any(), eq("pending"), any(), any());
    }

    @Test
    void pageQuery_带日期范围_调用时含startDateAndEndDate() {
        // given
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new org.apache.ibatis.session.Configuration(), ""), VoucherEntity.class);
        Page<VoucherEntity> page = new Page<>(1, 20, 1);
        page.setRecords(new ArrayList<>());
        when(voucherMapper.selectVoucherPage(any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        // when
        voucherService.pageQuery(voucherDtoWithDate(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31)));

        // then — startDate/endDate 应传递
        verify(voucherMapper).selectVoucherPage(
                any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(),
                eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 8, 31)));
    }

    private VoucherQueryDTO voucherDto(String status, String scope) {
        var dto = new VoucherQueryDTO();
        dto.setStatus(status);
        dto.setScope(scope);
        dto.setCurrent(1);
        dto.setSize(20);
        return dto;
    }

    private VoucherQueryDTO voucherDtoWithDate(LocalDate startDate, LocalDate endDate) {
        var dto = new VoucherQueryDTO();
        dto.setStatus("POSTED");
        dto.setScope("completed");
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setCurrent(1);
        dto.setSize(20);
        return dto;
    }
}
