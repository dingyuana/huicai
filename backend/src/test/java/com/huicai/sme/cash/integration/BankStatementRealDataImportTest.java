package com.huicai.sme.cash.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.business.entity.BankStatementEntity;
import com.huicai.base.business.mapper.BankStatementMapper;
import com.huicai.base.system.entity.UserEntity;
import com.huicai.common.context.EnterpriseContextHolder;
import com.huicai.common.test.AbstractMapperTest;
import com.huicai.common.test.SlowTest;
import com.huicai.config.security.LoginUser;
import com.huicai.sme.cash.entity.BankAccountEntity;
import com.huicai.sme.cash.mapper.BankAccountMapper;
import com.huicai.sme.cash.service.BankStatementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实银行流水 CSV 导入测试 — 使用真实银行对账单格式数据。
 *
 * <p>测试数据源：bank_statement_real_data.csv（15 条真实风格的银行流水）
 * 覆盖场景：收入/支出/税费/工资/手续费/利息等常见交易类型。
 *
 * <p>验证内容：
 * <ol>
 *   <li>CSV 导入行数正确（15 条全部导入）</li>
 *   <li>交易日期解析正确（LocalDate 格式）</li>
 *   <li>交易类型解析正确（收→INCOME, 付→EXPENSE）</li>
 *   <li>金额解析正确（BigDecimal 精度）</li>
 *   <li>对方户名/摘要/流水号正确解析</li>
 *   <li>导入后自动分类完成（分类字段不为空）</li>
 *   <li>收入/支出类型分布正确</li>
 * </ol>
 *
 * @SlowTest — 需要 Docker + Testcontainers
 */
@SlowTest
@DisplayName("银行流水 CSV 真实数据导入测试")
class BankStatementRealDataImportTest extends AbstractMapperTest {

    private static final String ACCOUNT_NAME = "测试基本户";
    private static final String ACCOUNT_NO = "6222021234567890";

    /**
     * 银行账户 id。t_bank_account.id 是 {@code GENERATED ALWAYS AS IDENTITY}，
     * 禁止显式赋值（P99 D 类：cannot insert a non-DEFAULT value into column "id"），
     * 由 DB 在 setUp() 中分配后回填。
     */
    private Long accountId;

    @Autowired
    private BankStatementService service;

    @Autowired
    private BankStatementMapper statementMapper;

    @Autowired
    private BankAccountMapper accountMapper;

    @BeforeEach
    void setUp() {
        // 导入服务内部构建 BankStatementEntity 时不显式赋 enterprise_id，
        // 依赖 MyMetaObjectHandler 从上下文回填；且查询经数据权限拦截器注入
        // enterprise_id 条件。测试需模拟真实请求上下文。
        EnterpriseContextHolder.set(1L);

        // 分类引擎 ClassificationRuleServiceImpl.match() 走
        // SecurityUtils.getCurrentEnterpriseId()，无登录态会抛「未登录」并被
        // importFromCsv 的 catch 吞掉，导致 classification 为 null。
        // 故需注入与真实请求等价的 SecurityContext。
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setUserType("ENTERPRISE");
        user.setEnterpriseId(1L);
        user.setAgencyId(null);
        user.setAgencyRole("SME_ADMIN");
        user.setStatus("ACTIVE");
        user.setDeleted(0);
        // 注意构造器参数顺序：第 3 个是 enterpriseId，第 4 个是 agencyId。
        // 规则引擎按 t_classification_rule.tenant_id = 当前企业过滤，
        // enterpriseId 传 null 会让规则全部落空并退化为方向兜底。
        LoginUser loginUser = new LoginUser(user, List.of(), 1L, null, "ENTERPRISE", "SME_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));

        // 清理本测试上一次运行遗留的流水（按账户号定位上一轮账户）
        BankAccountEntity previous = accountMapper.selectOne(
                new LambdaQueryWrapper<BankAccountEntity>()
                        .eq(BankAccountEntity::getAccountNo, ACCOUNT_NO)
                        .last("LIMIT 1"));
        if (previous != null && previous.getId() != null) {
            statementMapper.delete(new LambdaQueryWrapper<BankStatementEntity>()
                    .eq(BankStatementEntity::getAccountId, previous.getId()));
            accountMapper.deleteById(previous.getId());
        }

        // 创建银行账户，id 由 DB 分配
        BankAccountEntity account = new BankAccountEntity();
        account.setAccountName(ACCOUNT_NAME);
        account.setAccountNo(ACCOUNT_NO);
        account.setBankName("工商银行");
        account.setEnterpriseId(1L);
        accountMapper.insert(account);
        this.accountId = account.getId();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        EnterpriseContextHolder.clear();
    }

    @Test
    @DisplayName("导入 15 条真实风格银行流水 CSV")
    void importRealBankStatementCsv() throws IOException {
        // 读取 CSV 文件
        ClassPathResource resource = new ClassPathResource("bank_statement_real_data.csv");
        String csvContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        // 导入 CSV
        int imported = service.importFromCsv(accountId, csvContent);

        // 验证 1：导入行数
        assertEquals(15, imported, "应导入 15 条银行流水");

        // 验证 2：查询所有记录
        List<BankStatementEntity> allStmts = statementMapper.selectList(
                new LambdaQueryWrapper<BankStatementEntity>()
                        .eq(BankStatementEntity::getAccountId, accountId)
                        .orderByAsc(BankStatementEntity::getTxDate)
        );

        assertEquals(15, allStmts.size(), "DB 中应有 15 条记录");

        // 验证 3：第一条记录（收入 - 货款）
        BankStatementEntity first = allStmts.get(0);
        assertEquals("2026-07-01", first.getTxDate().toString(), "交易日期应为 2026-07-01");
        assertEquals("INCOME", first.getTxType(), "交易类型应为 INCOME（收）");
        assertEquals(0, new java.math.BigDecimal("50000.00").compareTo(first.getAmount()), "金额应为 50000");
        assertEquals("深圳科技有限公司", first.getCounterAccount(), "对方户名");
        assertTrue(first.getSummary().contains("货款"), "摘要应包含'货款'");

        // 验证 4：支出记录
        BankStatementEntity expense = allStmts.get(1);
        assertEquals("2026-07-02", expense.getTxDate().toString());
        assertEquals("EXPENSE", expense.getTxType(), "交易类型应为 EXPENSE（付）");
        assertEquals(0, new java.math.BigDecimal("12000.00").compareTo(expense.getAmount()));

        // 验证 5：分类结果不为空（导入后自动分类）
        for (BankStatementEntity stmt : allStmts) {
            assertNotNull(stmt.getClassification(), "导入后应自动分类，当前ID=" + stmt.getId() + " 摘要=" + stmt.getSummary());
        }

        // 验证 6：收入/支出分布（CSV 实为 6 条「收」+ 9 条「付」，
        // 含 2026-07-20 利息收入一行；原断言 5/10 与数据不符）
        long incomeCount = allStmts.stream().filter(s -> "INCOME".equals(s.getTxType())).count();
        long expenseCount = allStmts.stream().filter(s -> "EXPENSE".equals(s.getTxType())).count();
        assertEquals(6, incomeCount, "应有 6 条收入记录");
        assertEquals(9, expenseCount, "应有 9 条支出记录");

        // 验证 7：特定分类（工资、税费、手续费、利息）
        BankStatementEntity salary = allStmts.stream()
                .filter(s -> s.getSummary() != null && s.getSummary().contains("工资"))
                .findFirst().orElse(null);
        assertNotNull(salary, "应找到工资发放记录");
        // 工资应被分类为 salary_social
        assertEquals("salary_social", salary.getClassification(),
                "工资发放应被分类为 salary_social，当前=" + salary.getClassification());

        BankStatementEntity tax = allStmts.stream()
                .filter(s -> s.getSummary() != null && s.getSummary().contains("增值税"))
                .findFirst().orElse(null);
        assertNotNull(tax, "应找到税费记录");
        // 税费应被分类为 tax_withholding
        assertEquals("tax_withholding", tax.getClassification(),
                "增值税缴纳应被分类为 tax_withholding，当前=" + tax.getClassification());

        BankStatementEntity fee = allStmts.stream()
                .filter(s -> s.getSummary() != null && s.getSummary().contains("手续费"))
                .findFirst().orElse(null);
        assertNotNull(fee, "应找到手续费记录");
        // 手续费应被分类为 bank_interest_fee
        assertEquals("bank_interest_fee", fee.getClassification(),
                "手续费应被分类为 bank_interest_fee，当前=" + fee.getClassification());

        BankStatementEntity interest = allStmts.stream()
                .filter(s -> s.getSummary() != null && s.getSummary().contains("利息"))
                .findFirst().orElse(null);
        assertNotNull(interest, "应找到利息记录");
        // 利息收入应被分类为 bank_interest_fee
        assertEquals("bank_interest_fee", interest.getClassification(),
                "利息收入应被分类为 bank_interest_fee，当前=" + interest.getClassification());
    }
}