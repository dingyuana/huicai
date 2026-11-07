package com.huicai.sme.salary.service;

import com.huicai.sme.salary.dto.SalaryBillSaveDTO;
import com.huicai.sme.salary.vo.SalaryBillDetailVO;
import com.huicai.sme.salary.vo.SalaryBillItemVO;
import com.huicai.sme.salary.vo.SalaryBillVO;

import java.util.List;

/**
 * SPEC-P143（REQ-2026-160）：工资单服务。
 *
 * <p>状态机 {@code DRAFT --confirm(计提凭证)--> CONFIRMED --pay(发放凭证)--> PAID}；
 * 凭证走现有凭证流程（D-2），个税累计预扣法（D-1）。</p>
 */
public interface SalaryBillService {

    /** 保存工资单（按期间 upsert，整单替换明细）；仅 DRAFT 可改。 */
    Long save(SalaryBillSaveDTO dto, Long userId);

    /** 工资单列表（含凭证号回显）。 */
    List<SalaryBillVO> list();

    /** 工资单详情（员工级明细 + 个税快照）。 */
    SalaryBillDetailVO detail(Long billId);

    /** 工资单原始明细（纵表，编辑草稿回显用；confirm 后快照走 detail）。 */
    List<SalaryBillItemVO> items(Long billId);

    /** 确认：计算个税（累计预扣）+ 生成计提凭证 SALC-{id}。 */
    void confirm(Long billId, Long userId);

    /** 发放：生成发放凭证 SALP-{id}（借应付职工薪酬 / 贷个税+社保+银行）。 */
    void pay(Long billId, Long userId);

    /**
     * SPEC-P155 R1：缴纳社保，生成凭证 SALJ-{id}
     * （借 2211 单位社保 + 借 2241 个人社保代扣 / 贷 1002）；
     * 幂等：{@code socialPaid} 单标记，仅可缴纳一次；权限复用 salary:pay。
     */
    void paySocial(Long billId, Long userId);

    /** SPEC-P155 R3：工资条导出（xlsx，每员工一个行组，纵表项目明细+实发）。 */
    void exportPayroll(Long billId, jakarta.servlet.http.HttpServletResponse response);

    /** SPEC-P155 R3：个税申报表导出（xlsx，期间×员工台账快照，纯导出无计算）。 */
    void exportTaxDeclaration(Long billId, jakarta.servlet.http.HttpServletResponse response);
}
