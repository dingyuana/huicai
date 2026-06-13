package com.huicai.module.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.module.finance.entity.VoucherEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 凭证 Mapper
 */
@Mapper
public interface VoucherMapper extends BaseMapper<VoucherEntity> {

    /**
     * 分页查询凭证（含类型名称）
     */
    IPage<VoucherEntity> selectVoucherPage(Page<VoucherEntity> page,
                                           @Param("period") String period,
                                           @Param("status") String status,
                                           @Param("voucherTypeId") Long voucherTypeId,
                                           @Param("keyword") String keyword);

    /**
     * 按ID查询凭证详情
     */
    VoucherEntity selectVoucherDetail(@Param("id") Long id);

    /**
     * 查询指定期间的最大凭证号
     */
    String selectMaxVoucherNo(@Param("period") String period,
                              @Param("voucherTypeId") Long voucherTypeId);

    /**
     * 批量更新凭证状态
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids,
                          @Param("status") String status,
                          @Param("userId") Long userId);

    @Delete("DELETE FROM t_voucher WHERE source = #{source}")
    int deleteBySource(@Param("source") String source);

    @Delete("DELETE FROM t_voucher")
    int deleteAll();
}
