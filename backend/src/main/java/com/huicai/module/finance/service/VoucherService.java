package com.huicai.module.finance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.huicai.module.finance.dto.VoucherCreateDTO;
import com.huicai.module.finance.dto.VoucherQueryDTO;
import com.huicai.module.finance.dto.VoucherVO;
import com.huicai.module.finance.entity.VoucherEntity;

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
     * 记账(已审核→已记账)
     */
    void post(Long id, Long userId);

    /**
     * 批量记账
     */
    void batchPost(List<Long> ids, Long userId);

    /**
     * 红冲(生成红字凭证)
     */
    VoucherVO reverse(Long id, Long userId);
}
