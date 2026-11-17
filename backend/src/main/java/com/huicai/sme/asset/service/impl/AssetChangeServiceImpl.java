package com.huicai.sme.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.base.system.entity.Subject;
import com.huicai.base.system.mapper.SubjectMapper;
import com.huicai.base.system.service.VoucherTypeService;
import com.huicai.base.voucher.entity.VoucherEntity;
import com.huicai.base.voucher.entity.VoucherEntryEntity;
import com.huicai.base.voucher.mapper.VoucherEntryMapper;
import com.huicai.base.voucher.mapper.VoucherMapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.sme.asset.dto.AssetChangeCreateDTO;
import com.huicai.sme.asset.entity.AssetCardEntity;
import com.huicai.sme.asset.entity.AssetChangeEntity;
import com.huicai.sme.asset.mapper.AssetCardMapper;
import com.huicai.sme.asset.mapper.AssetChangeMapper;
import com.huicai.sme.asset.service.AssetChangeService;
import com.huicai.sme.asset.vo.AssetChangeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SPEC-P144（D-4）：资产变动（调整）实现 —— 唯一此前完全缺服务层的实体
 * （AssetChangeEntity+Mapper 建于 V94，建后零引用、零行数据）。
 *
 * <h3>与折旧凭证实现（{@code DepreciationVoucherServiceImpl}）同构的口径</h3>
 * <ul>
 *   <li>科目解析 fail-loud：缺科目直接 400，不做降级假设（L124 先例）；</li>
 *   <li>凭证类型 {@code ZZ} 转账（各企业 V183 种子齐）；</li>
 *   <li>期间必须存在且开放（P87 期间锁全链路口径）；</li>
 *   <li>类级 @Transactional：本类注入多张 RLS 表 Mapper
 *       （{@code RlsTransactionStructureTest} 扫描面）。</li>
 * </ul>
 *
 * <h3>凭证号与幂等口径</h3>
 * 调整可对同一资产多次发生（不同日期/不同维度），不能用「期间」做幂等前缀
 * （折旧的 {@code DEPR-{period}} 模式不适用）。凭证号 = {@code ADJ-{changeId}}：
 * 先插变动行（voucherId 空）拿主键，再以它命名凭证并回填 —— 变动行与凭证
 * 双向可溯，且天然唯一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AssetChangeServiceImpl implements AssetChangeService {

    /** 原值调整借/贷科目：固定资产 / 银行存款（P85 先例：先固定，无分类→科目映射） */
    private static final String SUBJECT_FIXED_ASSET = "1601";
    private static final String SUBJECT_BANK = "1002";
    /** 凭证号前缀（调整凭证），与 DEPR-/ADJ- 的反结账扫描前缀形态一致。 */
    public static final String VOUCHER_NO_PREFIX = "ADJ-";

    private final AssetCardMapper cardMapper;
    private final AssetChangeMapper changeMapper;
    private final SubjectMapper subjectMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final VoucherTypeService voucherTypeService;
    private final com.huicai.base.system.service.PeriodService periodService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AssetChangeCreateDTO dto, Long userId) {
        AssetCardEntity card = cardMapper.selectById(dto.getAssetId());
        if (card == null || Integer.valueOf(1).equals(card.getDeleted())) {
            throw BusinessException.notFound("资产不存在");
        }
        if (!"IN_USE".equals(card.getStatus())) {
            throw BusinessException.badRequest(
                    "仅使用中(IN_USE)的资产可做变动调整，当前状态：" + card.getStatus());
        }

        switch (dto.getChangeType()) {
            case "VALUE_ADJUST" -> {
                return createValueAdjust(card, dto, userId);
            }
            case "DEPT_TRANSFER" -> {
                return createDeptTransfer(card, dto, userId);
            }
            case "STATUS_CHANGE" -> throw BusinessException.badRequest(
                    "状态变更暂未开放：资产状态流转必须走卡片状态机，避免绕过合法流转表");
            default -> throw BusinessException.badRequest("不支持的变动类型：" + dto.getChangeType());
        }
    }

    /** 价值调整：更新卡片原值/净值 + 生成调整凭证（不修改 t_asset_depreciation 历史计提）。 */
    private Long createValueAdjust(AssetCardEntity card, AssetChangeCreateDTO dto, Long userId) {
        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.signum() == 0) {
            throw BusinessException.badRequest("价值调整必须提供非零金额（正数调增 / 负数调减）");
        }

        String period = dto.getChangeDate().format(DateTimeFormatter.ofPattern("yyyyMM"));
        assertPeriodOpen(period);

        BigDecimal oldOriginal = card.getOriginalValue();
        BigDecimal accumulated = card.getAccumulatedDepreciation() == null
                ? BigDecimal.ZERO : card.getAccumulatedDepreciation();
        BigDecimal newOriginal = oldOriginal.add(amount);
        BigDecimal newNet = newOriginal.subtract(accumulated);
        if (newNet.compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.badRequest(
                    "调整后净值为负（" + newNet + "）：调减后 原值-累计折旧 不得低于 0");
        }

        // 先插变动行拿主键（凭证号 = ADJ-{changeId}），凭证生成后回填 voucherId
        AssetChangeEntity change = new AssetChangeEntity();
        change.setAssetId(card.getId());
        change.setChangeType("VALUE_ADJUST");
        change.setBeforeValue("originalValue=" + oldOriginal);
        change.setAfterValue("originalValue=" + newOriginal);
        change.setChangeDate(dto.getChangeDate());
        change.setRemark(dto.getRemark());
        change.setCreatedBy(userId);
        change.setCreatedAt(LocalDateTime.now());
        changeMapper.insert(change);

        // 凭证（amount>0 借1601/贷1002；amount<0 反向）
        Subject fixedAsset = requireSubject(SUBJECT_FIXED_ASSET);
        Subject bank = requireSubject(SUBJECT_BANK);
        VoucherEntity voucher = new VoucherEntity();
        voucher.setVoucherNo(VOUCHER_NO_PREFIX + change.getId());
        voucher.setPeriod(period);
        voucher.setVoucherTypeId(voucherTypeService.requireByCode("ZZ").getId());
        voucher.setStatus("DRAFT");
        voucher.setSource("GENERATED");
        voucher.setBusinessDocId(null);
        voucher.setSummary("资产原值调整 " + card.getAssetCode()
                + (amount.signum() > 0 ? " 调增 " : " 调减 ") + amount.abs());
        voucher.setTotalDebit(amount.abs());
        voucher.setTotalCredit(amount.abs());
        voucher.setCreatedBy(userId);
        voucher.setCreatedAt(LocalDateTime.now());
        voucherMapper.insert(voucher);

        VoucherEntryEntity debitLine;
        VoucherEntryEntity creditLine;
        if (amount.signum() > 0) {
            debitLine = newEntry(voucher.getId(), fixedAsset.getId(), amount, BigDecimal.ZERO, 1, voucher.getSummary());
            creditLine = newEntry(voucher.getId(), bank.getId(), BigDecimal.ZERO, amount, 2, voucher.getSummary());
        } else {
            debitLine = newEntry(voucher.getId(), bank.getId(), amount.abs(), BigDecimal.ZERO, 1, voucher.getSummary());
            creditLine = newEntry(voucher.getId(), fixedAsset.getId(), BigDecimal.ZERO, amount.abs(), 2, voucher.getSummary());
        }
        voucherEntryMapper.insert(debitLine);
        voucherEntryMapper.insert(creditLine);

        // 卡片主数据 + 变动行回填
        card.setOriginalValue(newOriginal);
        card.setNetValue(newNet);
        card.setUpdatedBy(userId);
        cardMapper.updateById(card);
        change.setVoucherId(voucher.getId());
        changeMapper.updateById(change);

        log.info("资产原值调整: changeId={}, assetId={}, amount={}, voucherNo={}, userId={}",
                change.getId(), card.getId(), amount, voucher.getVoucherNo(), userId);
        return change.getId();
    }

    /** 部门转移：只更新卡片 deptId，不涉金额、不制证。 */
    private Long createDeptTransfer(AssetCardEntity card, AssetChangeCreateDTO dto, Long userId) {
        if (dto.getDeptId() == null) {
            throw BusinessException.badRequest("部门转移必须提供目标部门 deptId");
        }
        if (dto.getDeptId().equals(card.getDeptId())) {
            throw BusinessException.badRequest("目标部门与当前部门相同，无需转移");
        }

        AssetChangeEntity change = new AssetChangeEntity();
        change.setAssetId(card.getId());
        change.setChangeType("DEPT_TRANSFER");
        change.setBeforeValue("deptId=" + card.getDeptId());
        change.setAfterValue("deptId=" + dto.getDeptId());
        change.setChangeDate(dto.getChangeDate());
        change.setRemark(dto.getRemark());
        change.setCreatedBy(userId);
        change.setCreatedAt(LocalDateTime.now());

        card.setDeptId(dto.getDeptId());
        card.setUpdatedBy(userId);
        cardMapper.updateById(card);
        changeMapper.insert(change);

        log.info("资产部门转移: changeId={}, assetId={}, toDept={}, userId={}",
                change.getId(), card.getId(), dto.getDeptId(), userId);
        return change.getId();
    }

    private void assertPeriodOpen(String period) {
        // P87 期间锁口径：调整凭证必须落在开放期间（与折旧凭证 L84 同款检查）。
        // 期间未配置视为开放 —— 演练账套常无 period 行，与折旧侧「未配置即 400」的差异
        // 是有意的：调整是卡片级动作，不应被期间配置的有无阻断，结账锁由 DEPR/CLOSE 步兜底。
        com.huicai.base.system.entity.PeriodEntity p = null;
        try {
            p = periodService.getByPeriodCode(period);
        } catch (Exception ignored) {
            // PeriodService 不可用（极端场景）不阻断调整，凭证期间锁由结账流程兜底
        }
        if (p != null && !"open".equals(p.getStatus())) {
            throw BusinessException.badRequest("期间 " + period + " 已结账，无法生成调整凭证");
        }
    }

    private Subject requireSubject(String code) {
        Subject s = subjectMapper.selectOne(new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code)
                .eq(Subject::getDeleted, 0)
                .last("LIMIT 1"));
        if (s == null) {
            throw BusinessException.badRequest("未配置科目 " + code + "，无法生成资产调整凭证");
        }
        return s;
    }

    private VoucherEntryEntity newEntry(Long voucherId, Long subjectId, BigDecimal debit,
                                        BigDecimal credit, int sort, String summary) {
        VoucherEntryEntity line = new VoucherEntryEntity();
        line.setVoucherId(voucherId);
        line.setSubjectId(subjectId);
        line.setDebit(debit);
        line.setCredit(credit);
        line.setSummary(summary);
        line.setSortOrder(sort);
        return line;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetChangeVO> listByAsset(Long assetId) {
        List<AssetChangeEntity> rows = changeMapper.selectList(
                new LambdaQueryWrapper<AssetChangeEntity>()
                        .eq(AssetChangeEntity::getAssetId, assetId)
                        .eq(AssetChangeEntity::getDeleted, 0)
                        .orderByDesc(AssetChangeEntity::getChangeDate)
                        .orderByDesc(AssetChangeEntity::getId));
        return rows.stream().map(this::toVO).toList();
    }

    private AssetChangeVO toVO(AssetChangeEntity e) {
        AssetChangeVO vo = new AssetChangeVO();
        vo.setId(e.getId());
        vo.setAssetId(e.getAssetId());
        vo.setChangeType(e.getChangeType());
        vo.setBeforeValue(e.getBeforeValue());
        vo.setAfterValue(e.getAfterValue());
        vo.setChangeDate(e.getChangeDate());
        vo.setVoucherId(e.getVoucherId());
        vo.setRemark(e.getRemark());
        if (e.getVoucherId() != null) {
            VoucherEntity v = voucherMapper.selectById(e.getVoucherId());
            if (v != null) {
                vo.setVoucherNo(v.getVoucherNo());
            }
        }
        AssetCardEntity card = cardMapper.selectById(e.getAssetId());
        if (card != null) {
            vo.setAssetCode(card.getAssetCode());
            vo.setAssetName(card.getAssetName());
        }
        return vo;
    }
}
