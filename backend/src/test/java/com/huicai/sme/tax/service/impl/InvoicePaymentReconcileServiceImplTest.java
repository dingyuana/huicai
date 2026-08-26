package com.huicai.sme.tax.service.impl;

import com.huicai.sme.tax.dto.vo.InvoiceReconcileVO;
import com.huicai.sme.tax.mapper.InvoicePaymentReconcileMapper;
import com.huicai.sme.tax.service.InvoicePaymentReconcileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoicePaymentReconcileServiceImplTest {

    @Mock
    private InvoicePaymentReconcileMapper mapper;

    @InjectMocks
    private InvoicePaymentReconcileServiceImpl service;

    private InvoiceReconcileVO stub(Long id, BigDecimal total, BigDecimal paid,
                                    String cert, String declared, Boolean red, String status) {
        InvoiceReconcileVO v = new InvoiceReconcileVO();
        v.setInvoiceId(id);
        v.setAmount(total);
        v.setPaidAmount(paid);
        v.setUnpaidAmount(total.subtract(paid));
        v.setCertificationStatus(cert);
        v.setDeclaredStatus(declared);
        v.setHasRedFlushed(red);
        v.setReconcileStatus(status);
        return v;
    }

    @Test
    void queryInputReconcile_partialPayment_marksPartial() {
        InvoiceReconcileVO v = stub(1L, new BigDecimal("1000.00"), new BigDecimal("600.00"),
                "CERTIFIED", "DECLARED", false, "PARTIAL");
        when(mapper.queryInputReconcile(any(), any())).thenReturn(List.of(v));

        List<InvoiceReconcileVO> r = service.queryInputReconcile("202608", 10L);
        assertEquals(1, r.size());
        assertEquals("PARTIAL", r.get(0).getReconcileStatus());
        assertEquals(new BigDecimal("400.00"), r.get(0).getUnpaidAmount());
    }

    @Test
    void queryInputReconcile_fullyPaid_marksPaid() {
        InvoiceReconcileVO v = stub(2L, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                "CERTIFIED", "DECLARED", false, "PAID");
        when(mapper.queryInputReconcile(any(), any())).thenReturn(List.of(v));

        List<InvoiceReconcileVO> r = service.queryInputReconcile(null, null);
        assertEquals("PAID", r.get(0).getReconcileStatus());
    }

    @Test
    void queryInputReconcile_unpaid_noDoc_marksUnpaid() {
        InvoiceReconcileVO v = stub(3L, new BigDecimal("1000.00"), BigDecimal.ZERO,
                "UNCERTIFIED", "UNDECLARED", false, "UNPAID");
        when(mapper.queryInputReconcile(any(), any())).thenReturn(List.of(v));

        List<InvoiceReconcileVO> r = service.queryInputReconcile("202608", null);
        assertEquals("UNPAID", r.get(0).getReconcileStatus());
        assertEquals(new BigDecimal("1000.00"), r.get(0).getUnpaidAmount());
    }

    @Test
    void queryOutputReconcile_delegatesToMapper() {
        InvoiceReconcileVO v = stub(4L, new BigDecimal("2000.00"), new BigDecimal("2000.00"),
                "CERTIFIED", "DECLARED", false, "PAID");
        v.setCustomerName("甲方");
        when(mapper.queryOutputReconcile(any(), any())).thenReturn(List.of(v));

        List<InvoiceReconcileVO> r = service.queryOutputReconcile("202608", 20L);
        assertEquals(1, r.size());
        assertEquals("PAID", r.get(0).getReconcileStatus());
        assertEquals("甲方", r.get(0).getCustomerName());
    }
}
