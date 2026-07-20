package com.huicai.sme.tax.mapper;

import com.huicai.common.test.AbstractMapperTest;
import com.huicai.base.masterdata.entity.CustomerEntity;
import com.huicai.base.masterdata.mapper.CustomerMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.business.entity.OutputInvoiceEntity;
import com.huicai.base.business.mapper.OutputInvoiceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OutputInvoiceMapper 真实 DB 测试
 *
 * 验证：销售发票 Mapper 的 CRUD 操作与数据库约束正确性
 * 可发现 Mock 测试无法发现的问题：
 * - status 字段 check constraint 枚举值校验
 * - BigDecimal 精度丢失
 * - period 字段 varchar(6) 长度限制
 * - 外键约束（customerId 必须存在于 t_customer）
 */
public class OutputInvoiceMapperTest extends AbstractMapperTest {

    @Autowired
    private OutputInvoiceMapper outputInvoiceMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    private Long testCustomerId;
    private Long testSubjectId;

    /**
     * 每个测试前准备基础数据
     * 由于外键约束，必须先创建科目和客户
     */
    @BeforeEach
    void setupTestData() {
        // 1. 创建测试科目（用于客户关联）
        Subject subject = new Subject();
        subject.setCode("9999.OUT");  // 测试专用编码
        subject.setName("测试应收科目");
        subject.setDirection("debit");
        subject.setLevel(1);
        subject.setIsActive(true);
        subject.setIsLeaf(true);
        subject.setDeleted(0);
        subjectMapper.insert(subject);
        testSubjectId = subject.getId();

        // 2. 创建测试客户
        CustomerEntity customer = new CustomerEntity();
        customer.setCode("C-TEST-001");
        customer.setName("测试客户");
        customer.setContactPerson("李四");
        customer.setPhone("13900139000");
        customer.setEmail("customer@example.com");
        customer.setAddress("客户测试地址");
        customer.setTaxNo("91110101MB11111111");
        customer.setBankName("测试银行");
        customer.setBankAccount("0987654321");
        customer.setCreditLimit(new BigDecimal("500000.00"));
        customer.setCreditDays(30);
        customer.setSubjectId(testSubjectId);
        customer.setIsActive(true);
        customer.setRemark("测试客户-销售发票测试用");
        customer.setDeleted(0);
        customerMapper.insert(customer);
        testCustomerId = customer.getId();
    }

    /**
     * 场景 1：插入测试
     * 验证：所有必填字段可正确插入，主键自动生成
     */
    @Test
    void insert_shouldReturnId() {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setInvoiceNo("INV-TEST-001");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity.setPeriod("202606");  // varchar(6)
        entity.setCustomerId(testCustomerId);
        entity.setCustomerName("测试客户");
        entity.setAmount(new BigDecimal("10000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("1300.00"));
        entity.setTotalAmount(new BigDecimal("11300.00"));
        entity.setInvoiceType("SPECIAL");  // 专用发票
        entity.setStatus("PENDING_CONFIRM");
        entity.setCreatedBy(1L);
        entity.setDeleted(0);

        int rows = outputInvoiceMapper.insert(entity);

        assertEquals(1, rows);
        assertNotNull(entity.getId());
    }

    /**
     * 场景 2：根据 ID 查询测试
     * 验证：插入的数据可正确查询，金额和税率精度无丢失
     */
    @Test
    void selectById_shouldReturnCorrectData() {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setInvoiceNo("INV-TEST-002");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity.setPeriod("202606");
        entity.setCustomerId(testCustomerId);
        entity.setCustomerName("测试客户");
        entity.setAmount(new BigDecimal("15000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("1950.00"));
        entity.setTotalAmount(new BigDecimal("16950.00"));
        entity.setInvoiceType("SPECIAL");
        entity.setStatus("PENDING_CONFIRM");
        entity.setRemark("销售发票查询验证");
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        outputInvoiceMapper.insert(entity);

        OutputInvoiceEntity found = outputInvoiceMapper.selectById(entity.getId());

        assertNotNull(found);
        assertEquals("INV-TEST-002", found.getInvoiceNo());
        assertEquals("202606", found.getPeriod());
        assertEquals(LocalDate.of(2026, 6, 27), found.getInvoiceDate());
        assertEquals(0, new BigDecimal("15000.00").compareTo(found.getAmount()));
        assertEquals(0, new BigDecimal("0.13").compareTo(found.getTaxRate()));
        assertEquals(0, new BigDecimal("1950.00").compareTo(found.getTaxAmount()));
        assertEquals(0, new BigDecimal("16950.00").compareTo(found.getTotalAmount()));
        assertEquals("SPECIAL", found.getInvoiceType());
        assertEquals("PENDING_CONFIRM", found.getStatus());
        assertEquals("销售发票查询验证", found.getRemark());
    }

    /**
     * 场景 3：更新测试
     * 验证：状态和金额可正确更新
     */
    @Test
    void updateById_shouldUpdateCorrectly() {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setInvoiceNo("INV-TEST-003");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity.setPeriod("202606");
        entity.setCustomerId(testCustomerId);
        entity.setCustomerName("测试客户");
        entity.setAmount(new BigDecimal("20000.00"));
        entity.setTaxRate(new BigDecimal("0.13"));
        entity.setTaxAmount(new BigDecimal("2600.00"));
        entity.setTotalAmount(new BigDecimal("22600.00"));
        entity.setInvoiceType("SPECIAL");
        entity.setStatus("PENDING_CONFIRM");
        entity.setRemark("更新前");
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        outputInvoiceMapper.insert(entity);

        // 更新：从待审核变为已审核
        entity.setStatus("CONFIRMED");
        entity.setRemark("更新后-已审核");
        int rows = outputInvoiceMapper.updateById(entity);

        assertEquals(1, rows);
        OutputInvoiceEntity updated = outputInvoiceMapper.selectById(entity.getId());
        assertEquals("CONFIRMED", updated.getStatus());
        assertEquals("更新后-已审核", updated.getRemark());
    }

    /**
     * 场景 4：删除测试
     * 验证：记录可正确删除（逻辑删除）
     */
    @Test
    void deleteById_shouldSoftDeleteCorrectly() {
        OutputInvoiceEntity entity = new OutputInvoiceEntity();
        entity.setInvoiceNo("INV-TEST-004");
        entity.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity.setPeriod("202606");
        entity.setCustomerId(testCustomerId);
        entity.setCustomerName("测试客户");
        entity.setAmount(new BigDecimal("5000.00"));
        entity.setTaxRate(new BigDecimal("0.06"));
        entity.setTaxAmount(new BigDecimal("300.00"));
        entity.setTotalAmount(new BigDecimal("5300.00"));
        entity.setInvoiceType("PLAIN");
        entity.setStatus("PENDING_CONFIRM");
        entity.setCreatedBy(1L);
        entity.setDeleted(0);
        outputInvoiceMapper.insert(entity);

        int rows = outputInvoiceMapper.deleteById(entity.getId());

        assertEquals(1, rows);
        OutputInvoiceEntity deleted = outputInvoiceMapper.selectById(entity.getId());
        assertNull(deleted);  // @TableLogic 自动过滤
    }

    /**
     * 场景 5：不同税率测试
     * 验证：13%、9%、6% 各种税率的精度正确
     */
    @Test
    void differentTaxRates_shouldPreservePrecision() {
        // 13% 税率
        OutputInvoiceEntity entity1 = new OutputInvoiceEntity();
        entity1.setInvoiceNo("INV-TEST-13P");
        entity1.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity1.setPeriod("202606");
        entity1.setCustomerId(testCustomerId);
        entity1.setCustomerName("测试客户");
        entity1.setAmount(new BigDecimal("10000.00"));
        entity1.setTaxRate(new BigDecimal("0.13"));
        entity1.setTaxAmount(new BigDecimal("1300.00"));
        entity1.setTotalAmount(new BigDecimal("11300.00"));
        entity1.setInvoiceType("SPECIAL");
        entity1.setStatus("PENDING_CONFIRM");
        entity1.setCreatedBy(1L);
        entity1.setDeleted(0);
        outputInvoiceMapper.insert(entity1);

        // 9% 税率
        OutputInvoiceEntity entity2 = new OutputInvoiceEntity();
        entity2.setInvoiceNo("INV-TEST-9P");
        entity2.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity2.setPeriod("202606");
        entity2.setCustomerId(testCustomerId);
        entity2.setCustomerName("测试客户");
        entity2.setAmount(new BigDecimal("10000.00"));
        entity2.setTaxRate(new BigDecimal("0.09"));
        entity2.setTaxAmount(new BigDecimal("900.00"));
        entity2.setTotalAmount(new BigDecimal("10900.00"));
        entity2.setInvoiceType("SPECIAL");
        entity2.setStatus("PENDING_CONFIRM");
        entity2.setCreatedBy(1L);
        entity2.setDeleted(0);
        outputInvoiceMapper.insert(entity2);

        // 6% 税率
        OutputInvoiceEntity entity3 = new OutputInvoiceEntity();
        entity3.setInvoiceNo("INV-TEST-6P");
        entity3.setInvoiceDate(LocalDate.of(2026, 6, 27));
        entity3.setPeriod("202606");
        entity3.setCustomerId(testCustomerId);
        entity3.setCustomerName("测试客户");
        entity3.setAmount(new BigDecimal("10000.00"));
        entity3.setTaxRate(new BigDecimal("0.06"));
        entity3.setTaxAmount(new BigDecimal("600.00"));
        entity3.setTotalAmount(new BigDecimal("10600.00"));
        entity3.setInvoiceType("PLAIN");
        entity3.setStatus("PENDING_CONFIRM");
        entity3.setCreatedBy(1L);
        entity3.setDeleted(0);
        outputInvoiceMapper.insert(entity3);

        // 验证
        OutputInvoiceEntity found1 = outputInvoiceMapper.selectById(entity1.getId());
        OutputInvoiceEntity found2 = outputInvoiceMapper.selectById(entity2.getId());
        OutputInvoiceEntity found3 = outputInvoiceMapper.selectById(entity3.getId());

        assertEquals(0, new BigDecimal("0.13").compareTo(found1.getTaxRate()));
        assertEquals(0, new BigDecimal("0.09").compareTo(found2.getTaxRate()));
        assertEquals(0, new BigDecimal("0.06").compareTo(found3.getTaxRate()));
    }

    /**
     * 场景 6：Entity-DB 字段对齐测试（修复 P40 两个 bug 的回归保护）
     *
     * 验证：
     * - auditedBy/auditedAt 有 @TableField(exist = false)，SELECT 不报 "column does not exist"
     * - aiMappingResult 有 typeHandler = JsonbTypeHandler.class，UPDATE 不报 JSONB 类型不匹配
     */
    @Test
    void entityDbAlignment_shouldNotThrowColumnErrors() {
        var e = new OutputInvoiceEntity();
        e.setInvoiceNo("INV-ALIGN-001");
        e.setInvoiceDate(LocalDate.of(2026, 7, 8));
        e.setPeriod("202607");
        e.setCustomerId(testCustomerId);
        e.setCustomerName("对齐测试客户");
        e.setAmount(new BigDecimal("10000.00"));
        e.setTaxRate(new BigDecimal("0.13"));
        e.setTaxAmount(new BigDecimal("1300.00"));
        e.setTotalAmount(new BigDecimal("11300.00"));
        e.setAmountExTax(new BigDecimal("10000.00"));
        e.setInvoiceType("SPECIAL");
        e.setStatus("PENDING_CONFIRM");
        e.setCreatedBy(1L);
        e.setDeleted(0);
        e.setAiMappingResult("{\"account_code\":\"1122\",\"confidence\":0.95}");
        outputInvoiceMapper.insert(e);
        assertNotNull(e.getId());

        OutputInvoiceEntity found = outputInvoiceMapper.selectById(e.getId());
        assertNotNull(found);
        assertEquals("PENDING_CONFIRM", found.getStatus());

        e.setStatus("PENDING_REVIEW");
        assertDoesNotThrow(() -> outputInvoiceMapper.updateById(e));

        OutputInvoiceEntity updated = outputInvoiceMapper.selectById(e.getId());
        assertEquals("PENDING_REVIEW", updated.getStatus());

        e.setAiMappingResult("{\"account_code\":\"6001\",\"confidence\":0.88}");
        assertDoesNotThrow(() -> outputInvoiceMapper.updateById(e));

        OutputInvoiceEntity finalCheck = outputInvoiceMapper.selectById(e.getId());
        assertEquals("{\"account_code\":\"6001\",\"confidence\":0.88}", finalCheck.getAiMappingResult());
    }
}
