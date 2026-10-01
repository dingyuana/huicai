package com.huicai.base.system.mapper;

import com.huicai.base.system.entity.DeptEntity;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DeptMapper 真库补充（REQ-2026-131 / P104 第 3 批：Mock 同义反复归零）
 *
 * <p>{@code DeptMapperTest} 用 mock mock 被测对象本身，断言「我写的桩被返回」，
 * 其中「deleteById 应接受 ID 参数」一条尤其具有误导性 —— mock 根本不会执行
 * 软删除，而真实行为（铁律 #12）从未被覆盖。
 */
@DisplayName("P104 DeptMapper 真库补充")
class DeptMapperConstraintsRealDBTest extends AbstractMapperTest {

    @Autowired
    private DeptMapper deptMapper;

    private DeptEntity newDept(String suffix) {
        DeptEntity d = new DeptEntity();
        d.setName("P104部门-" + suffix);
        d.setDeptCode("P104_DEPT_" + suffix + "_" + System.nanoTime());
        d.setParentId(0L);
        d.setSortOrder(1);
        d.setDeleted(0);
        return d;
    }

    @Test
    @DisplayName("uq_dept_code 唯一约束真实生效")
    void deptCodeIsUnique() {
        String code = "P104_DEPT_DUP_" + System.nanoTime();
        DeptEntity first = newDept("DUPA");
        first.setDeptCode(code);
        deptMapper.insert(first);

        DeptEntity second = newDept("DUPB");
        second.setDeptCode(code);
        assertThrows(DuplicateKeyException.class, () -> deptMapper.insert(second),
                "重复 dept_code 未被拒绝");
    }

    @Test
    @DisplayName("🔴 deleteById 是软删除：行仍在但 deleted=1")
    void deleteByIdIsSoftDelete() {
        DeptEntity d = newDept("DEL");
        deptMapper.insert(d);
        Long id = d.getId();

        assertEquals(1, deptMapper.deleteById(id));

        Integer physical = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM t_dept WHERE id = ?", Integer.class, id);
        assertEquals(1, physical, "行被物理删除 —— 违反逻辑删除铁律 #12");

        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT deleted FROM t_dept WHERE id = ?", Integer.class, id);
        assertEquals(1, deleted, "deleted 未置 1");
    }

    @Test
    @DisplayName("dept_name 为 NULL 必须被 NOT NULL 拒绝（真实约束，非 mock 假设）")
    void nullNameViolatesNotNull() {
        DeptEntity d = newDept("NONAME");
        d.setName(null);
        assertThrows(DataIntegrityViolationException.class, () -> deptMapper.insert(d),
                "dept_name 为 NULL 却插入成功 ⇒ NOT NULL 约束未生效");
    }

    @Test
    @DisplayName("软删除后默认查询查不到（MP 逻辑删除生效）")
    void selectByIdExcludesSoftDeleted() {
        DeptEntity d = newDept("LIST");
        deptMapper.insert(d);
        deptMapper.deleteById(d.getId());
        org.junit.jupiter.api.Assertions.assertNull(deptMapper.selectById(d.getId()));
    }

    @Test
    @DisplayName("插入后 id 由数据库生成且回填到实体")
    void insertGeneratesId() {
        DeptEntity d = newDept("ID");
        assertEquals(1, deptMapper.insert(d));
        assertNotNull(d.getId());
        assertNotNull(deptMapper.selectById(d.getId()));
    }
}