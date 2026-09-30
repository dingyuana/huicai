package com.huicai.base.report.mapper;

import com.huicai.base.balance.entity.SubjectBalanceEntity;
import com.huicai.base.balance.mapper.SubjectBalanceMapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.common.test.AbstractMapperTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P94 REQ-092：现金勾稽科目口径必须覆盖 1012 其他货币资金。
 *
 * 为什么必须走真实 DB：cashSubjectBalance 是 @Select 注解 SQL，改动点是 WHERE 的科目范围。
 * Mockito 只能证明参数透传，证明不了"1012 有没有被这条 SQL 选中"——旧 LIKE '100%' 会漏掉它，
 * 而这种错误在页面上表现为"勾稽差异莫名不为 0"，极难定位。
 *
 * 断言策略：seed 数据与本次插入混在同一期间，故一律用「插入前后差值」断言，不用绝对值。
 * 与 CashFlowPeriodRangeRealDBTest 同策略。
 *
 * <p>注：基类 AbstractMapperTest 现已默认设置企业上下文，数据权限拦截器会按
 * {@code enterprise_id} 过滤，故本类须把上下文切到自己的 {@link #ENT_ID}。
 */
class CashSubjectBalanceRealDBTest extends AbstractMapperTest {

    /** 独立 enterpriseId，避免与 Flyway seed 的 (code, enterprise_id) 唯一约束冲突。 */
    private static final long ENT_ID = 9902L;

    @org.junit.jupiter.api.BeforeEach
    void useOwnEnterprise() {
        useEnterprise(ENT_ID);
    }
    private static final String PERIOD = "202401";

    @Autowired
    private ReportDataMapper reportDataMapper;

    @Autowired
    private SubjectMapper subjectMapper;

    @Autowired
    private SubjectBalanceMapper subjectBalanceMapper;

    private Long insertSubject(String code, String name) {
        Subject s = new Subject();
        s.setCode(code);
        s.setName(name);
        s.setLevel(1);
        s.setDirection("debit");
        s.setIsLeaf(true);
        s.setIsActive(true);
        s.setEnterpriseId(ENT_ID);
        s.setDeleted(0);
        assertEquals(1, subjectMapper.insert(s));
        return s.getId();
    }

    private void insertBalance(Long subjectId, String begin, String end) {
        SubjectBalanceEntity b = new SubjectBalanceEntity();
        b.setSubjectId(subjectId);
        b.setYear(2024);
        b.setPeriod(PERIOD);
        b.setBeginBalance(new BigDecimal(begin));
        b.setDebitTotal(BigDecimal.ZERO);
        b.setCreditTotal(BigDecimal.ZERO);
        b.setEndBalance(new BigDecimal(end));
        // enterprise_id 由 BaseEntity 填充处理器写入，测试无登录上下文，须显式设置
        b.setEnterpriseId(ENT_ID);
        assertEquals(1, subjectBalanceMapper.insert(b));
    }

    private BigDecimal endCash() {
        Map<String, Object> row = reportDataMapper.cashSubjectBalance(PERIOD);
        assertNotNull(row, "cashSubjectBalance 必须返回一行");
        return new BigDecimal(row.get("end_cash").toString());
    }

    private BigDecimal beginCash() {
        return new BigDecimal(
                reportDataMapper.cashSubjectBalance(PERIOD).get("begin_cash").toString());
    }

    @Test
    void 现金口径含1012其他货币资金() {
        BigDecimal baseEnd = endCash();

        insertBalance(insertSubject("1012", "其他货币资金"), "1000", "7000");

        assertEquals(0, new BigDecimal("7000").compareTo(endCash().subtract(baseEnd)),
                "1012 期末余额必须计入现金合计（回归：旧 LIKE '100%' 口径漏掉 1012，增量为 0）");
    }

    @Test
    void 期初口径同样含1012() {
        BigDecimal baseBegin = beginCash();

        insertBalance(insertSubject("1012", "其他货币资金"), "2500", "7000");

        assertEquals(0, new BigDecimal("2500").compareTo(beginCash().subtract(baseBegin)),
                "1012 期初余额必须计入（期初/期末必须同口径，否则期初+净流量≠期末）");
    }

    @Test
    void 非货币资金科目不纳入现金口径() {
        BigDecimal baseEnd = endCash();

        insertBalance(insertSubject("1122", "应收账款"), "5000", "9000");

        assertEquals(0, BigDecimal.ZERO.compareTo(endCash().subtract(baseEnd)),
                "负向：1122 应收账款不是货币资金，不得计入现金合计（防止把口径放宽成 LIKE）");
    }
}
