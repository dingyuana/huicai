package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.PeriodEntity;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.PeriodService;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.constant.VoucherType;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.dto.DepreciationVoucherResult;
import com.huicai.sme.asset.entity.AssetDepreciationEntity;
import com.huicai.sme.asset.mapper.AssetDepreciationMapper;
import com.huicai.sme.asset.service.DepreciationVoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 折旧自动制证（P85-C）实现.
 *
 * <p>设计要点：
 * <ol>
 *   <li><b>只读已计提结果，不自行算折旧</b>——金额全部来自 t_asset_depreciation
 *       （由 AssetCardService.depreciatePeriod 产生）。守铁律#1：人是唯一审核主体，
 *       计提口径由人工确认，制证只负责把它转成凭证。</li>
 *   <li><b>直接 voucherMapper.insert 而非 VoucherService.create()</b>——
 *       create() 会用 voucherNoService 生成递增凭证号，无法固定为 DEPR-{period}；
 *       而 DEPR-{period} 既是幂等键又是反结账扫描前缀（P87 reopenPeriod 已按此前缀扫描）。
 *       与 sme/tax（TaxServiceImpl）、sme/arap（BadDebtServiceImpl）直接 insert 模式一致。</li>
 *   <li><b>幂等检测口径与 generateProfitCarryOver 同构</b>——likeRight 前缀 + isNull(reversedFrom)
 *       + deleted=0，排除已红冲的行。重复调用报错而非静默跳过，防人工误操作被吞。</li>
 *   <li><b>不自动计提、不自动审核、不自动过账</b>——凭证落 DRAFT，后续走人工审核过账。</li>
 * </ol>
 *
 * <p>注意：enterprise_id 由 MyMetaObjectHandler 从 EnterpriseContextHolder 自动填充，
 * 本类不显式设置（同 sme/tax 直接 insert 的既有做法）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepreciationVoucherServiceImpl implements DepreciationVoucherService {

    /** 借方科目：管理费用（现行科目表实测存在，无资产分类→费用科目映射，P85 先固定） */
    private static final String SUBJECT_EXPENSE = "6602";
    /** 贷方科目：累计折旧 */
    private static final String SUBJECT_ACCUM_DEPR = "1602";
    /** 凭证号前缀：同时是 P87 reopenPeriod 的反结账扫描前缀 */
    public static final String VOUCHER_NO_PREFIX = "DEPR-";

    private final AssetDepreciationMapper depreciationMapper;
    private final SubjectMapper subjectMapper;
    private final PeriodService periodService;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DepreciationVoucherResult generate(String period, Long userId) {
        // 1. 期间必须存在
        PeriodEntity periodEntity = periodService.getByPeriodCode(period);
        if (periodEntity == null) {
            throw BusinessException.badRequest("期间 " + period + " 未配置");
        }

        // 2. 期间必须仍开放（已结账期间不能再制证，与期间锁全链路 P87 一致）
        if (!"open".equals(periodEntity.getStatus())) {
            throw BusinessException.badRequest("期间 " + period + " 已结账，无法生成折旧凭证");
        }

        // 3. 幂等检测：已存在 DEPR-{period} 凭证（未删除、非红冲）时拒绝重复制证
        Long existing = voucherMapper.selectCount(
                new LambdaQueryWrapper<VoucherEntity>()
                        .likeRight(VoucherEntity::getVoucherNo, VOUCHER_NO_PREFIX + period)
                        .isNull(VoucherEntity::getReversedFrom)
                        .eq(VoucherEntity::getDeleted, 0));
        if (existing != null && existing > 0) {
            throw BusinessException.badRequest(
                    "期间 " + period + " 已存在 " + existing + " 张折旧凭证 "
                            + VOUCHER_NO_PREFIX + period + "，请勿重复制证");
        }

        // 4. 读取该期间已计提的折旧记录（金额来源；本服务绝不自行计算）
        List<AssetDepreciationEntity> records = depreciationMapper.selectList(
                new LambdaQueryWrapper<AssetDepreciationEntity>()
                        .eq(AssetDepreciationEntity::getPeriod, period)
                        .eq(AssetDepreciationEntity::getDeleted, 0));
        if (records.isEmpty()) {
            throw BusinessException.badRequest(
                    "期间 " + period + " 无可制证的折旧数据，请先在资产模块计提折旧");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (AssetDepreciationEntity rec : records) {
            BigDecimal amt = rec.getDepreciationAmount();
            if (amt == null) {
                throw BusinessException.badRequest(
                        "资产折旧记录 ID=" + rec.getId() + " 金额为空，请先检查计提数据");
            }
            if (amt.signum() <= 0) {
                throw BusinessException.badRequest(
                        "资产折旧记录 ID=" + rec.getId() + " 金额非正(" + amt + ")，跳过制证并检查计提数据");
            }
            total = total.add(amt);
        }

        // 5. 科目解析（缺科目直接报错，不做降级假设）
        Subject expense = requireSubject(SUBJECT_EXPENSE);
        Subject accumDepreciation = requireSubject(SUBJECT_ACCUM_DEPR);

        // 6. 组装凭证头
        String voucherNo = VOUCHER_NO_PREFIX + period;
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(voucherNo);
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(VoucherType.ZZ);
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setSummary("自动计提折旧: " + period);
        voucher.setTotalDebit(total);
        voucher.setTotalCredit(total);
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());
        voucherMapper.insert(voucher);

        // 7. 两行分录：借 6602 管理费用 / 贷 1602 累计折旧
        VoucherEntryEntity debitLine = newEntry(voucher.getId(), expense.getId(), total, BigDecimal.ZERO, 1,
                "计提折旧 " + period);
        VoucherEntryEntity creditLine = newEntry(voucher.getId(), accumDepreciation.getId(), BigDecimal.ZERO, total, 2,
                "计提折旧 " + period);
        voucherEntryMapper.insert(debitLine);
        voucherEntryMapper.insert(creditLine);

        // 8. 回写溯源：t_asset_depreciation.voucher_id 指向本凭证（供折旧报表反向定位凭证）
        for (AssetDepreciationEntity rec : records) {
            if (rec.getVoucherId() == null) {
                rec.setVoucherId(voucher.getId());
                depreciationMapper.updateById(rec);
            }
        }

        log.info("生成折旧凭证: id={}, voucherNo={}, period={}, amount={}, details={}, userId={}",
                voucher.getId(), voucherNo, period, total, records.size(), userId);
        return new DepreciationVoucherResult(voucher.getId(), voucherNo, total, total,
                records.size(), "DRAFT");
    }

    private Subject requireSubject(String code) {
        Subject s = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code)
                .eq(Subject::getDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) {
            throw BusinessException.badRequest("未配置科目 " + code + "，无法生成折旧凭证");
        }
        return s;
    }

    private VoucherEntryEntity newEntry(Long voucherId, Long subjectId, BigDecimal debit, BigDecimal credit,
                                        int sort, String summary) {
        VoucherEntryEntity line = new VoucherEntryEntity();
        line.setVoucherId(voucherId);
        line.setSubjectId(subjectId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setSummary(summary);
        line.setSortOrder(sort);
        return line;
    }
}
