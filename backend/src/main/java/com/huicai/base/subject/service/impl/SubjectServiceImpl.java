package com.huicai.base.subject.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.subject.entity.Subject;
import com.huicai.base.subject.mapper.SubjectMapper;
import com.huicai.base.subject.model.dto.SubjectCreateDTO;
import com.huicai.base.subject.model.dto.SubjectUpdateDTO;
import com.huicai.base.subject.model.vo.SubjectTreeVO;
import com.huicai.base.subject.service.SubjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectMapper subjectMapper;

    // ===== 方向常量 =====
    private static final String DIRECTION_DEBIT = "debit";
    private static final String DIRECTION_CREDIT = "credit";

    @Override
    public List<SubjectTreeVO> getTree() {
        // 查询所有未删除的科目
        List<Subject> allSubjects = subjectMapper.selectList(
                new LambdaQueryWrapper<Subject>()
                        .orderByAsc(Subject::getLevel)
                        .orderByAsc(Subject::getCode));

        // 构建树
        return buildTree(allSubjects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Subject create(SubjectCreateDTO dto) {
        // 校验编码唯一性
        checkCodeUnique(dto.getCode(), null);

        // 校验借贷方向
        validateDirection(dto.getDirection());

        // 校验辅助核算类型
        validateAuxCalcType(dto.getAuxCalcType());

        // 计算层级
        int level = 1;
        if (dto.getParentId() != null) {
            Subject parent = subjectMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw BusinessException.notFound("父科目不存在");
            }
            level = parent.getLevel() + 1;

            // 父科目不再是末级
            if (parent.getIsLeaf()) {
                parent.setIsLeaf(false);
                subjectMapper.updateById(parent);
            }
        }

        // 新建科目
        Subject subject = new Subject();
        BeanUtil.copyProperties(dto, subject);
        subject.setLevel(level);
        subject.setIsLeaf(true);

        subjectMapper.insert(subject);
        log.info("新增科目: id={}, code={}, name={}, level={}", subject.getId(), subject.getCode(), subject.getName(), level);
        return subject;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Subject update(Long id, SubjectUpdateDTO dto) {
        Subject existing = subjectMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("科目不存在");
        }

        // 校验编码唯一性（排除自身）
        if (!existing.getCode().equals(dto.getCode())) {
            checkCodeUnique(dto.getCode(), id);
        }

        // 校验借贷方向
        validateDirection(dto.getDirection());

        // 校验辅助核算类型
        validateAuxCalcType(dto.getAuxCalcType());

        // 不允许修改 parent_id（防止树结构混乱）
        BeanUtil.copyProperties(dto, existing);
        existing.setId(id);

        subjectMapper.updateById(existing);
        log.info("更新科目: id={}, code={}, name={}", id, dto.getCode(), dto.getName());
        return subjectMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.notFound("科目不存在");
        }

        // 检查是否有子科目
        Long childCount = subjectMapper.selectCount(
                new LambdaQueryWrapper<Subject>().eq(Subject::getParentId, id));
        if (childCount > 0) {
            throw BusinessException.badRequest("该科目下有子科目，无法删除");
        }

        subjectMapper.deleteById(id);
        log.info("删除科目: id={}, code={}, name={}", id, subject.getCode(), subject.getName());
    }

    @Override
    public Subject getById(Long id) {
        Subject subject = subjectMapper.selectById(id);
        if (subject == null) {
            throw BusinessException.notFound("科目不存在");
        }
        return subject;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importStandard() {
        // 检查是否已有科目数据（含逻辑删除的记录）
        Long totalCount = subjectMapper.selectCountPhysical();
        if (totalCount > 0) {
            throw BusinessException.badRequest("系统已存在科目记录（含已删除科目），请先手动清空所有科目数据后再导入");
        }

        // 国家标准的 6 类一级科目定义
        // 格式: {code, name, direction, category}
        List<StandardSubject> level1Subjects = Arrays.asList(
            // ===== 资产类 (1xxx) =====
            new StandardSubject("1001", "库存现金", "debit", "资产"),
            new StandardSubject("1002", "银行存款", "debit", "资产"),
            new StandardSubject("1012", "其他货币资金", "debit", "资产"),
            new StandardSubject("1101", "交易性金融资产", "debit", "资产"),
            new StandardSubject("1121", "应收票据", "debit", "资产"),
            new StandardSubject("1122", "应收账款", "debit", "资产"),
            new StandardSubject("1123", "预付账款", "debit", "资产"),
            new StandardSubject("1131", "应收股利", "debit", "资产"),
            new StandardSubject("1132", "应收利息", "debit", "资产"),
            new StandardSubject("1221", "其他应收款", "debit", "资产"),
            new StandardSubject("1231", "坏账准备", "credit", "资产"),
            new StandardSubject("1401", "材料采购", "debit", "资产"),
            new StandardSubject("1402", "在途物资", "debit", "资产"),
            new StandardSubject("1403", "原材料", "debit", "资产"),
            new StandardSubject("1404", "材料成本差异", "debit", "资产"),
            new StandardSubject("1405", "库存商品", "debit", "资产"),
            new StandardSubject("1406", "发出商品", "debit", "资产"),
            new StandardSubject("1407", "商品进销差价", "debit", "资产"),
            new StandardSubject("1408", "委托加工物资", "debit", "资产"),
            new StandardSubject("1411", "周转材料", "debit", "资产"),
            new StandardSubject("1471", "存货跌价准备", "credit", "资产"),
            new StandardSubject("1501", "债权投资", "debit", "资产"),
            new StandardSubject("1502", "债权投资减值准备", "credit", "资产"),
            new StandardSubject("1511", "长期股权投资", "debit", "资产"),
            new StandardSubject("1512", "长期股权投资减值准备", "credit", "资产"),
            new StandardSubject("1521", "投资性房地产", "debit", "资产"),
            new StandardSubject("1531", "长期应收款", "debit", "资产"),
            new StandardSubject("1601", "固定资产", "debit", "资产"),
            new StandardSubject("1602", "累计折旧", "credit", "资产"),
            new StandardSubject("1603", "固定资产减值准备", "credit", "资产"),
            new StandardSubject("1604", "在建工程", "debit", "资产"),
            new StandardSubject("1605", "工程物资", "debit", "资产"),
            new StandardSubject("1606", "固定资产清理", "debit", "资产"),
            new StandardSubject("1701", "无形资产", "debit", "资产"),
            new StandardSubject("1702", "累计摊销", "credit", "资产"),
            new StandardSubject("1703", "无形资产减值准备", "credit", "资产"),
            new StandardSubject("1711", "商誉", "debit", "资产"),
            new StandardSubject("1801", "长期待摊费用", "debit", "资产"),
            new StandardSubject("1811", "递延所得税资产", "debit", "资产"),
            new StandardSubject("1901", "待处理财产损溢", "debit", "资产"),

            // ===== 负债类 (2xxx) =====
            new StandardSubject("2001", "短期借款", "credit", "负债"),
            new StandardSubject("2101", "交易性金融负债", "credit", "负债"),
            new StandardSubject("2201", "应付票据", "credit", "负债"),
            new StandardSubject("2202", "应付账款", "credit", "负债"),
            new StandardSubject("2203", "预收账款", "credit", "负债"),
            new StandardSubject("2211", "应付职工薪酬", "credit", "负债"),
            new StandardSubject("2221", "应交税费", "credit", "负债"),
            new StandardSubject("2231", "应付股利", "credit", "负债"),
            new StandardSubject("2232", "应付利息", "credit", "负债"),
            new StandardSubject("2241", "其他应付款", "credit", "负债"),
            new StandardSubject("2401", "递延收益", "credit", "负债"),
            new StandardSubject("2501", "长期借款", "credit", "负债"),
            new StandardSubject("2502", "应付债券", "credit", "负债"),
            new StandardSubject("2701", "长期应付款", "credit", "负债"),
            new StandardSubject("2711", "专项应付款", "credit", "负债"),
            new StandardSubject("2801", "预计负债", "credit", "负债"),
            new StandardSubject("2901", "递延所得税负债", "credit", "负债"),

            // ===== 共同类 (3xxx) =====
            new StandardSubject("3101", "衍生工具", "debit", "共同"),
            new StandardSubject("3201", "套期工具", "debit", "共同"),
            new StandardSubject("3202", "被套期项目", "debit", "共同"),

            // ===== 所有者权益类 (4xxx) =====
            new StandardSubject("4001", "实收资本", "credit", "权益"),
            new StandardSubject("4002", "资本公积", "credit", "权益"),
            new StandardSubject("4101", "盈余公积", "credit", "权益"),
            new StandardSubject("4103", "本年利润", "credit", "权益"),
            new StandardSubject("4104", "利润分配", "credit", "权益"),
            new StandardSubject("4201", "库存股", "debit", "权益"),

            // ===== 成本类 (5xxx) =====
            new StandardSubject("5001", "生产成本", "debit", "成本"),
            new StandardSubject("5101", "制造费用", "debit", "成本"),
            new StandardSubject("5201", "劳务成本", "debit", "成本"),
            new StandardSubject("5301", "研发支出", "debit", "成本"),
            new StandardSubject("5401", "工程施工", "debit", "成本"),
            new StandardSubject("5402", "工程结算", "credit", "成本"),
            new StandardSubject("5403", "机械作业", "debit", "成本"),

            // ===== 损益类 (6xxx) =====
            new StandardSubject("6001", "主营业务收入", "credit", "损益"),
            new StandardSubject("6051", "其他业务收入", "credit", "损益"),
            new StandardSubject("6101", "公允价值变动损益", "credit", "损益"),
            new StandardSubject("6111", "投资收益", "credit", "损益"),
            new StandardSubject("6115", "资产处置损益", "credit", "损益"),
            new StandardSubject("6117", "其他收益", "credit", "损益"),
            new StandardSubject("6301", "营业外收入", "credit", "损益"),
            new StandardSubject("6401", "主营业务成本", "debit", "损益"),
            new StandardSubject("6402", "其他业务成本", "debit", "损益"),
            new StandardSubject("6403", "税金及附加", "debit", "损益"),
            new StandardSubject("6601", "销售费用", "debit", "损益"),
            new StandardSubject("6602", "管理费用", "debit", "损益"),
            new StandardSubject("6603", "财务费用", "debit", "损益"),
            new StandardSubject("6701", "资产减值损失", "debit", "损益"),
            new StandardSubject("6711", "营业外支出", "debit", "损益"),
            new StandardSubject("6801", "所得税费用", "debit", "损益"),
            new StandardSubject("6901", "以前年度损益调整", "credit", "损益")
        );

        int count = 0;
        for (StandardSubject s : level1Subjects) {
            Subject subject = new Subject();
            subject.setCode(s.code);
            subject.setName(s.name);
            subject.setLevel(1);
            subject.setDirection(s.direction);
            subject.setIsLeaf(true);
            subject.setIsActive(true);
            subject.setRemark(s.category + "类");

            // 为应收账款和预收账款启用客户辅助核算
            if ("1122".equals(s.code) || "2203".equals(s.code)) {
                subject.setAuxCalcType("customer");
            }
            // 为应付账款和预付账款启用供应商辅助核算
            if ("2202".equals(s.code) || "1123".equals(s.code)) {
                subject.setAuxCalcType("vendor");
            }
            // 为其他应收款启用员工辅助核算
            if ("1221".equals(s.code)) {
                subject.setAuxCalcType("employee");
            }

            subjectMapper.insert(subject);
            count++;
        }
        log.info("一键导入国家标准科目完成，共导入 {} 个一级科目", count);
        return count;
    }

    // ===================== 私有方法 =====================

    /**
     * 校验科目编码唯一性
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<Subject> wrapper = new LambdaQueryWrapper<Subject>()
                .eq(Subject::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Subject::getId, excludeId);
        }
        Long count = subjectMapper.selectCount(wrapper);
        if (count > 0) {
            throw BusinessException.conflict("科目编码 '" + code + "' 已存在");
        }
    }

    /**
     * 校验借贷方向
     */
    private void validateDirection(String direction) {
        if (!DIRECTION_DEBIT.equals(direction) && !DIRECTION_CREDIT.equals(direction)) {
            throw BusinessException.badRequest("借贷方向不合法: 只能为 debit(借方) 或 credit(贷方)");
        }
    }

    /**
     * 校验辅助核算类型
     */
    private void validateAuxCalcType(String auxCalcType) {
        if (auxCalcType == null || auxCalcType.isEmpty()) {
            return;
        }
        switch (auxCalcType) {
            case "customer":
            case "vendor":
            case "department":
            case "project":
            case "employee":
                return;
            default:
                throw BusinessException.badRequest(
                        "辅助核算类型不合法: customer/vendor/department/project/employee");
        }
    }

    /**
     * 构建科目树
     */
    private List<SubjectTreeVO> buildTree(List<Subject> subjects) {
        // 先转为 VO 列表
        List<SubjectTreeVO> voList = subjects.stream()
                .map(this::toTreeVO)
                .toList();

        // 按 parentId 分组
        Map<Long, List<SubjectTreeVO>> parentChildMap = voList.stream()
                .filter(vo -> vo.getParentId() != null)
                .collect(Collectors.groupingBy(SubjectTreeVO::getParentId));

        // 为每个节点挂载子节点
        for (SubjectTreeVO vo : voList) {
            List<SubjectTreeVO> children = parentChildMap.get(vo.getId());
            if (children != null) {
                vo.setChildren(children);
            } else {
                vo.setChildren(new ArrayList<>());
            }
        }

        // 返回根节点（parentId == null 的科目）
        return voList.stream()
                .filter(vo -> vo.getParentId() == null)
                .collect(Collectors.toList());
    }

    /**
     * Subject -> SubjectTreeVO
     */
    private SubjectTreeVO toTreeVO(Subject subject) {
        SubjectTreeVO vo = new SubjectTreeVO();
        BeanUtil.copyProperties(subject, vo);
        return vo;
    }

    // 标准科目临时结构
    private record StandardSubject(String code, String name, String direction, String category) {}
}