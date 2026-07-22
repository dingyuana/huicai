package com.huicai.base.voucher.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.base.voucher.dto.VoucherCreateDTO;
import com.huicai.base.voucher.dto.VoucherQueryDTO;
import com.huicai.base.voucher.dto.VoucherTemplateVO;
import com.huicai.base.voucher.dto.VoucherVO;

import java.io.InputStream;
import java.util.List;

/**
 * 凭证 Service
 */
public interface VoucherService {

    /**
     * 分页查询凭证
     */
    IPage<VoucherVO> pageQuery(VoucherQueryDTO queryDTO);

    /**
     * 查询凭证详情
     */
    VoucherVO getDetail(Long id);

    /**
     * 创建凭证
     */
    VoucherVO create(VoucherCreateDTO dto, Long userId);

    /**
     * 更新凭证(仅草稿状态可更新)
     */
    VoucherVO update(VoucherCreateDTO dto, Long userId);

    /**
     * 删除凭证(逻辑删除)
     */
    void delete(Long id);

    /**
     * 提交凭证(草稿→已提交)
     */
    void submit(Long id, Long userId);

    /**
     * 批量提交
     */
    void batchSubmit(List<Long> ids, Long userId);

    /**
     * 审核凭证(已提交→已审核)
     */
    void audit(Long id, Long userId);

    /**
     * 批量审核
     */
    void batchAudit(List<Long> ids, Long userId);

    /**
     * 记账凭证 (AUDITED → POSTED).
     */
    void post(Long id, Long userId);

    /**
     * 结账凭证 (POSTED → CLOSED).
     */
    void close(Long id, Long userId);

    /**
     * 批量记账
     */
    void batchPost(List<Long> ids, Long userId);

    /**
     * 红冲(生成红字凭证)
     */
    VoucherVO reverse(Long id, Long userId);

    /**
     * 驳回(SUBMITTED → DRAFT, 记录驳回原因)
     */
    void reject(Long id, Long userId, String reason);

    /**
     * 反过账(POSTED → AUDITED, 仅纠错用)
     */
    void unpost(Long id, Long userId);

    /**
     * 根据凭证类型 ID 获取绑定的模板 (含分录行), 无绑定返回 null
     */
    VoucherTemplateVO getTemplateByVoucherType(Long voucherTypeId);

    /**
     * 导出凭证到 Excel
     */
    InputStream exportToExcel(VoucherQueryDTO queryDTO);
}
