package com.huicai.module.system.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.system.entity.PeriodEntity;
import com.huicai.module.system.mapper.PeriodMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Period Mapper 真实 DB 测试.
 * <p>
 * ✅ 正向：自动生成 period_code / start_date / end_date
 * ✅ 负向：NOT NULL 约束校验
 * ✅ 约束：period_code UNIQUE、month CHECK
 */
class PeriodMapperTest extends AbstractMapperTest {

    @Autowired
    private PeriodMapper periodMapper;

    @Test
    void save_shouldAutoGenerateAllFields() {
        PeriodEntity entity = new PeriodEntity();
        entity.setYear(2026);
        entity.setMonth(7);
        // 故意不设 period_code / start_date / end_date，验证自动生成
        entity.setStatus("open");
        periodMapper.insert(entity);

        assertNotNull(entity.getId());
        assertEquals("202607", entity.getPeriodCode(), "period_code 应从 year+month 自动生成");
        assertEquals(LocalDate.of(2026, 7, 1), entity.getStartDate());
        assertEquals(LocalDate.of(2026, 7, 31), entity.getEndDate());
    }

    @Test
    void save_shouldEnforceNotNullStartDate() {
        PeriodEntity entity = new PeriodEntity();
        entity.setPeriodCode("202607");
        entity.setYear(2026);
        entity.setMonth(7);
        // start_date 为 NOT NULL，不设置应报错
        assertThrows(Exception.class, () -> periodMapper.insert(entity),
                "start_date 为 NOT NULL，插入应失败");
    }

    @Test
    void periodCode_shouldBeUnique() {
        PeriodEntity e1 = new PeriodEntity();
        e1.setPeriodCode("202607");
        e1.setYear(2026); e1.setMonth(7);
        e1.setStartDate(LocalDate.of(2026, 7, 1));
        e1.setEndDate(LocalDate.of(2026, 7, 31));
        e1.setStatus("open");
        periodMapper.insert(e1);

        PeriodEntity e2 = new PeriodEntity();
        e2.setPeriodCode("202607"); // 与 e1 相同
        e2.setYear(2026); e2.setMonth(8);
        e2.setStartDate(LocalDate.of(2026, 8, 1));
        e2.setEndDate(LocalDate.of(2026, 8, 31));
        e2.setStatus("open");
        assertThrows(Exception.class, () -> periodMapper.insert(e2),
                "period_code 有 UNIQUE 约束，重复应失败");
    }

    @Test
    void save_shouldEnforceMonthCheck() {
        PeriodEntity entity = new PeriodEntity();
        entity.setPeriodCode("202613");
        entity.setYear(2026);
        entity.setMonth(13); // 非法月份;
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 1, 31));
        entity.setStatus("open");
        assertThrows(Exception.class, () -> periodMapper.insert(entity),
                "month 有 CHECK 约束 (1-12)，13 应失败");
    }

    @Test
    void save_shouldEnforceStatusCheck() {
        PeriodEntity entity = new PeriodEntity();
        entity.setPeriodCode("202607");
        entity.setYear(2026); entity.setMonth(7);
        entity.setStartDate(LocalDate.of(2026, 7, 1));
        entity.setEndDate(LocalDate.of(2026, 7, 31));
        entity.setStatus("INVALID"); // 非法状态值;
        assertThrows(Exception.class, () -> periodMapper.insert(entity),
                "status 有 CHECK 约束 (open/closed/locked)，INVALID 应失败");
    }
}