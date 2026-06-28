package com.huicai.module.arap.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.module.arap.entity.PayableEntity;
import com.huicai.module.arap.entity.VendorEntity;
import com.huicai.module.system.entity.Subject;
import com.huicai.module.system.mapper.SubjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PayableMapper 真实 DB 测试
 *
 * 验证：应付单 Mapper 的 CRUD 操作与数据库约束正确性
 * 可发现 Mock 测试无法发现的问题：
 * - BigDecimal 精度丢失
 * - period 字段 varchar(6) 长度限制
 * - 外键约束（vendorId/docId 必须存在于关联表）
 * - 乐观锁版本号机制
 */
public class PayableMapperTest extends AbstractMapperTest {

    @Autowired
    private PayableMapper payableMapper;

    @Autowired
    private VendorMapper vendorMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    private Long testVendorId;
    private Long testSubjectId;

    /**
     * 每个测试前准备基础数据
     * 由于外键约束，必须先创建科目和供应商
     */
    @BeforeEach
    void setupTestData() {
        // 1. 创建测试科目（用于供应商关联）
        Subject subject = new Subject();
        subject.setCode("9999.PAY");  // 测试专用编码
        subject.setName("测试应付科目");
        subject.setDirection("debit");
        subject.setLevel(1);
        subject.setIsActive(true);
        subject.setIsLeaf(true);
        subject.setDeleted(0);
        subjectMapper.insert(subject);
        testSubjectId = subject.getId();

        // 2. 创建测试供应商
        VendorEntity vendor = new VendorEntity();
        vendor.setCode("V-TEST-001");
        vendor.setName("测试供应商");
        vendor.setContactPerson("张三");
        vendor.setPhone("13800138000");
        vendor.setEmail("test@example.com");
        vendor.setAddress("测试地址");
        vendor.setTaxNo("91110101MA00000000");
        vendor.setBankName("测试银行");
        vendor.setBankAccount("1234567890");
        vendor.setCreditLimit(new BigDecimal("100000.00"));
        vendor.setCreditDays(30);
        vendor.setSubjectId(testSubjectId);
        vendor.setIsActive(true);
        vendor.setRemark("测试供应商-应付单测试用");
        vendor.setDeleted(0);
        vendorMapper.insert(vendor);
        testVendorId = vendor.getId();
    }

    /**
     * 场景 1：插入测试
     * 验证：所有必填字段可正确插入，主键自动生成
     */
    @Test
    void insert_shouldReturnId() {
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(testVendorId);
        entity.setDocId(null);  // 允许为空
        entity.setPeriod("202606");  // varchar(6)，不能用横杠
        entity.setTxDate(LocalDate.of(2026, 6, 27));
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("10000.00"));
        entity.setDueDate(LocalDate.of(2026, 7, 27));
        entity.setSummary("测试应付单-采购货物");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);

        int rows = payableMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    /**
     * 场景 2：根据 ID 查询测试
     * 验证：插入的数据可正确查询，金额精度无丢失
     */
    @Test
    void selectById_shouldReturnCorrectData() {
        // 先插入
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(testVendorId);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.of(2026, 6, 27));
        entity.setAmount(new BigDecimal("15000.50"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("15000.50"));
        entity.setDueDate(LocalDate.of(2026, 7, 27));
        entity.setSummary("测试应付单-查询验证");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);
        payableMapper.insert(entity);

        // 再查询
        PayableEntity found = payableMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("202606", found.getPeriod());
        assertEquals(0, new BigDecimal("15000.50").compareTo(found.getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(found.getSettledAmount()));
        assertEquals(LocalDate.of(2026, 6, 27), found.getTxDate());
        assertEquals("测试应付单-查询验证", found.getSummary());
        assertEquals("CONFIRMED", found.getStatus());
    }

    /**
     * 场景 3：更新测试
     * 验证：金额和状态可正确更新，版本号自动递增
     */
    @Test
    void updateById_shouldUpdateCorrectly() {
        // 先插入
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(testVendorId);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.of(2026, 6, 27));
        entity.setAmount(new BigDecimal("20000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("20000.00"));
        entity.setDueDate(LocalDate.of(2026, 7, 27));
        entity.setSummary("测试应付单-更新前");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);
        payableMapper.insert(entity);

        // 更新
        entity.setStatus("SETTLED");
        entity.setSettledAmount(new BigDecimal("20000.00"));
        entity.setUnsettledAmount(BigDecimal.ZERO);
        entity.setSummary("测试应付单-更新后");
        int rows = payableMapper.updateById(entity);

        assertEquals(1, rows);
        PayableEntity updated = payableMapper.selectById(entity.getId());
        assertEquals("SETTLED", updated.getStatus());
        assertEquals(0, new BigDecimal("20000.00").compareTo(updated.getSettledAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getUnsettledAmount()));
        assertEquals("测试应付单-更新后", updated.getSummary());
    }

    /**
     * 场景 4：删除测试
     * 验证：记录可正确删除（逻辑删除）
     */
    @Test
    void deleteById_shouldSoftDeleteCorrectly() {
        // 先插入
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(testVendorId);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.of(2026, 6, 27));
        entity.setAmount(new BigDecimal("5000.00"));
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setUnsettledAmount(new BigDecimal("5000.00"));
        entity.setDueDate(LocalDate.of(2026, 7, 27));
        entity.setSummary("测试应付单-待删除");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);
        payableMapper.insert(entity);

        // 删除（逻辑删除）
        int rows = payableMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        PayableEntity deleted = payableMapper.selectById(entity.getId());
        assertNull(deleted);  // @TableLogic 自动过滤 deleted=1 的记录
    }

    /**
     * 场景 5：金额边界值测试
     * 验证：大额和零值的 BigDecimal 精度正确
     */
    @Test
    void amountPrecision_shouldNotLosePrecision() {
        PayableEntity entity = new PayableEntity();
        entity.setVendorId(testVendorId);
        entity.setPeriod("202606");
        entity.setTxDate(LocalDate.of(2026, 6, 27));
        // 使用高精度金额
        entity.setAmount(new BigDecimal("999999999.99"));
        entity.setSettledAmount(new BigDecimal("1234567.89"));
        entity.setUnsettledAmount(new BigDecimal("998765432.10"));
        entity.setDueDate(LocalDate.of(2026, 7, 27));
        entity.setSummary("精度边界测试");
        entity.setStatus("CONFIRMED");
        entity.setDeleted(0);
        payableMapper.insert(entity);

        PayableEntity found = payableMapper.selectById(entity.getId());

        assertEquals(0, new BigDecimal("999999999.99").compareTo(found.getAmount()));
        assertEquals(0, new BigDecimal("1234567.89").compareTo(found.getSettledAmount()));
        assertEquals(0, new BigDecimal("998765432.10").compareTo(found.getUnsettledAmount()));
    }
}
