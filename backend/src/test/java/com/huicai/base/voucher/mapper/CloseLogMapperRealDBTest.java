package com.huicai.base.voucher.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.voucher.entity.CloseLogEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * t_close_log 结账日志表 真实 DB 测试（P85）.
 *
 * 验证 V151 建表与 CloseLogEntity 的列映射：
 * <ul>
 *   <li>建表成功 + insert/select 往返不抛 PSQLException（列缺失会报 column does not exist）</li>
 *   <li>operator_id 自定义列持久化 —— 不能依赖 BaseEntity.createdBy
 *       （该字段带 @TableField(exist=false)，MyBatis-Plus 会忽略，写进去也不落库）</li>
 *   <li>chk_close_log_result CHECK 约束：非法 result 值被数据库拒绝</li>
 * </ul>
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
class CloseLogMapperRealDBTest extends AbstractMapperTest {

    @Autowired
    private CloseLogMapper closeLogMapper;

    private CloseLogEntity insertLog(String action, String result) {
        CloseLogEntity e = new CloseLogEntity();
        e.setPeriod("202608");
        e.setAction(action);
        e.setOperatorId(999L);
        e.setResult(result);
        e.setDetail("期间 202608 " + action + " 完成");
        e.setDurationMs(42L);
        e.setDeleted(0);
        assertEquals(1, closeLogMapper.insert(e));
        assertNotNull(e.getId());
        return e;
    }

    @Test
    @DisplayName("t_close_log 建表+往返：operator_id 自定义列落库，不依赖 BaseEntity.createdBy")
    void closeLog_roundTripOperatorIdPersisted() {
        CloseLogEntity saved = insertLog("CLOSE", "success");

        CloseLogEntity loaded = closeLogMapper.selectById(saved.getId());
        assertNotNull(loaded);
        assertEquals("202608", loaded.getPeriod());
        assertEquals("CLOSE", loaded.getAction());
        assertEquals("success", loaded.getResult());
        assertEquals("42", String.valueOf(loaded.getDurationMs()));
        assertEquals("期间 202608 CLOSE 完成", loaded.getDetail());
        // 核心断言：operator_id 必须通过自定义列落库
        assertEquals(999L, loaded.getOperatorId(), "operator_id 自定义列必须持久化");
        // BaseEntity.createdBy 带 @TableField(exist=false)，不应由 insert 落库
        assertNull(loaded.getCreatedBy(), "createdBy 是 exist=false 字段，insert 不会落库");
        assertNotNull(loaded.getCreatedAt(), "createdAt 应由 FieldFill.INSERT 填充");
    }

    @Test
    @DisplayName("chk_close_log_result 约束：非法 result 值被数据库拒绝")
    void closeLog_resultConstraintRejectsIllegalValue() {
        CloseLogEntity e = new CloseLogEntity();
        e.setPeriod("202608");
        e.setAction("CLOSE");
        e.setResult("unknown_value"); // 不在 (success, fail) 内
        e.setDeleted(0);

        assertThrows(Exception.class, () -> closeLogMapper.insert(e),
                "chk_close_log_result CHECK 约束应拒绝非法 result 值");
    }

    @Test
    @DisplayName("按期间查询且倒序返回（与 listCloseLog 的查询路径一致）")
    void closeLog_queryByPeriodOrderDesc() {
        CloseLogEntity a = insertLog("CHECK", "success");
        CloseLogEntity b = insertLog("GENERATE", "success");
        CloseLogEntity c = insertLog("REOPEN", "fail");
        // 干扰项：其他期间（独立新实体，不复用上面已插入的行）
        CloseLogEntity other = new CloseLogEntity();
        other.setPeriod("202607");
        other.setAction("CLOSE");
        other.setResult("success");
        other.setDeleted(0);
        closeLogMapper.insert(other);

        var rows = closeLogMapper.selectList(
                new LambdaQueryWrapper<CloseLogEntity>()
                        .eq(CloseLogEntity::getPeriod, "202608")
                        .eq(CloseLogEntity::getDeleted, 0)
                        .orderByDesc(CloseLogEntity::getId));

        assertEquals(3, rows.size(), "只应返回 202608 的 3 条，其他期间被排除");
        assertTrue(rows.stream().noneMatch(r -> "202607".equals(r.getPeriod())));
        // 倒序：最后一条应为最新插入的 REOPEN
        assertEquals("REOPEN", rows.get(0).getAction());
        assertEquals("fail", rows.get(0).getResult());
        assertEquals("CHECK", rows.get(2).getAction());
    }
}
