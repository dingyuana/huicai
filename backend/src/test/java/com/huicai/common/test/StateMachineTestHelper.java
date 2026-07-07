package com.huicai.common.test;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.module.arap.constant.ArapStatus;
import com.huicai.module.finance.entity.BusinessDocEntity;
import com.huicai.module.finance.entity.BusinessDocEntryEntity;
import com.huicai.module.finance.entity.VoucherEntity;
import com.huicai.module.finance.entity.VoucherEntryEntity;
import com.huicai.module.finance.mapper.BusinessDocEntryMapper;
import com.huicai.module.finance.mapper.BusinessDocMapper;
import com.huicai.module.finance.mapper.VoucherEntryMapper;
import com.huicai.module.finance.mapper.VoucherMapper;
import com.huicai.module.tax.entity.OutputInvoiceEntity;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 状态机测试辅助工具.
 *
 * <p>提供两类能力：
 * <ol>
 *   <li>{@code verifyNoXxx()} —— 负向断言系列，验证某个 Mapper 没有被调用过，
 *       专门用于捕获"不该做的做了"类缺陷（如审核阶段创建了凭证）</li>
 *   <li>{@code createEntity()} —— 快速构造常见测试实体</li>
 * </ol>
 *
 * <h3>用法示例</h3>
 * <pre>{@code
 * @ExtendWith(MockitoExtension.class)
 * class OutputInvoiceStateMachineServiceImplTest {
 *     @Mock OutputInvoiceMapper invoiceMapper;
 *     @Mock VoucherMapper voucherMapper;
 *
 *     private OutputInvoiceStateMachineServiceImpl service;
 *
 *     @BeforeEach void setup() {
 *         service = new OutputInvoiceStateMachineServiceImpl(invoiceMapper, ...);
 *     }
 *
 *     @Test
 *     void confirm_shouldChangeStatusOnly() {
 *         // given
 *         OutputInvoiceEntity inv = StateMachineTestHelper.createInvoice(1L, InvoiceStatus.PENDING_REVIEW);
 *         when(invoiceMapper.selectById(1L)).thenReturn(inv);
 *
 *         // when
 *         service.confirm(1L, 1L);
 *
 *         // then — 正向断言
 *         assertEquals(InvoiceStatus.CONFIRMED, inv.getStatus());
 *
 *         // then — 负向断言：确认阶段不应创建凭证
 *         StateMachineTestHelper.verifyNoVoucherCreated(voucherMapper, null);
 *     }
 * }
 * }</pre>
 *
 * @see <a href="file://docs/process/state-machine-test-checklist.md">状态机测试契约检查清单</a>
 */
public final class StateMachineTestHelper {

    private StateMachineTestHelper() {}

    // ===================== 负向断言系列 =====================

    /**
     * 验证未创建任何凭证或凭证分录.
     * <p>所有状态转换方法中，除显式 "markVouchered" 外都应当调用此方法.
     */
    public static void verifyNoVoucherCreated(
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper) {
        if (voucherMapper != null) {
            verify(voucherMapper, never()).insert(any(VoucherEntity.class));
        }
        if (voucherEntryMapper != null) {
            verify(voucherEntryMapper, never()).insert(any(VoucherEntryEntity.class));
        }
    }

    /**
     * 验证未创建任何业务单据或业务单分录.
     * <p>适用于仅变更状态、不应生成业务单据的转换方法.
     */
    public static void verifyNoDocumentCreated(
            BusinessDocMapper docMapper,
            BusinessDocEntryMapper docEntryMapper) {
        if (docMapper != null) {
            verify(docMapper, never()).insert(any(BusinessDocEntity.class));
        }
        if (docEntryMapper != null) {
            verify(docEntryMapper, never()).insert(any(BusinessDocEntryEntity.class));
        }
    }

    /**
     * 验证未对任何指定 Mapper 执行 insert.
     * <p>通用兜底：当一个状态机方法不应有创建任何资源的副作用时，传入选中的 mapper.
     */
    public static void verifyNoInsert(BaseMapper<?>... mappers) {
        for (BaseMapper<?> mapper : mappers) {
            if (mapper != null) {
                verify(mapper, never()).insert(any());
            }
        }
    }

    /**
     * 验证未对任何指定 Mapper 执行 update.
     */
    public static void verifyNoUpdate(BaseMapper<?>... mappers) {
        for (BaseMapper<?> mapper : mappers) {
            if (mapper != null) {
                verify(mapper, never()).update(any());
            }
        }
    }

    // ===================== 实体工厂 =====================

    public static OutputInvoiceEntity createInvoice(Long id, String status) {
        OutputInvoiceEntity e = new OutputInvoiceEntity();
        e.setId(id);
        e.setStatus(status);
        e.setInvoiceDate(LocalDate.now());
        e.setPeriod("202606");
        e.setCustomerName("测试客户");
        e.setAmount(new BigDecimal("1000.00"));
        e.setTaxAmount(new BigDecimal("130.00"));
        e.setTotalAmount(new BigDecimal("1130.00"));
        return e;
    }

    public static BusinessDocEntity createBusinessDoc(Long id, String status, String docType) {
        BusinessDocEntity e = new BusinessDocEntity();
        e.setId(id);
        e.setStatus(status);
        e.setDocType(docType);
        e.setAmount(new BigDecimal("1000.00"));
        e.setSettledAmount(BigDecimal.ZERO);
        e.setUnsettledAmount(new BigDecimal("1000.00"));
        return e;
    }

    public static VoucherEntity createVoucher(Long id, String status) {
        VoucherEntity e = new VoucherEntity();
        e.setId(id);
        e.setStatus(status);
        e.setPeriod("202606");
        return e;
    }

    // ===================== 正向断言辅助 =====================

    /**
     * 断言 BusinessException 的 message 包含预期关键词.
     */
    public static void assertBusinessErrorContains(Exception e, String keyword) {
        assertInstanceOf(BusinessException.class, e);
        assertTrue(e.getMessage().contains(keyword),
                () -> "期望异常包含「" + keyword + "」，实际: " + e.getMessage());
    }

    // ===================== ArgumentCaptor 快捷 =====================

    /**
     * 捕获 updateById 调用的参数并返回捕获器（可用于进一步验证写入的字段值）.
     */
    public static <T> ArgumentCaptor<T> captureUpdate(BaseMapper<T> mapper, Class<T> entityClass) {
        ArgumentCaptor<T> captor = ArgumentCaptor.forClass(entityClass);
        verify(mapper).updateById(captor.capture());
        return captor;
    }
}
