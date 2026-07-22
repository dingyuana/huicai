package com.huicai.base.masterdata.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huicai.common.exception.BusinessException;
import com.huicai.base.masterdata.entity.VendorEntity;
import com.huicai.base.masterdata.mapper.VendorMapper;
import com.huicai.base.masterdata.service.VendorService;
import com.huicai.base.business.mapper.BusinessDocMapper;
import com.huicai.base.system.model.dto.ImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorMapper mapper;
    private final BusinessDocMapper businessDocMapper;

    // ===== 模板列定义 =====
    private static final List<String> TEMPLATE_HEADERS = List.of(
            "供应商编码", "供应商名称", "联系人", "电话", "邮箱", "地址", "备注"
    );
    private static final int COL_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_CONTACT = 2;
    private static final int COL_PHONE = 3;
    private static final int COL_EMAIL = 4;
    private static final int COL_ADDRESS = 5;
    private static final int COL_REMARK = 6;

    @Override
    public IPage<VendorEntity> pageQuery(String keyword, Boolean isActive, Integer current, Integer size) {
        Page<VendorEntity> page = new Page<>(
                current == null ? 1 : current,
                size == null ? 20 : size
        );
        LambdaQueryWrapper<VendorEntity> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(VendorEntity::getCode, keyword)
                    .or().like(VendorEntity::getName, keyword)
                    .or().like(VendorEntity::getContactPerson, keyword));
        }
        if (isActive != null) {
            wrapper.eq(VendorEntity::getIsActive, isActive);
        }
        wrapper.orderByAsc(VendorEntity::getCode);
        return mapper.selectPage(page, wrapper);
    }

    @Override
    public List<VendorEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<VendorEntity>()
                .eq(VendorEntity::getIsActive, true)
                .orderByAsc(VendorEntity::getCode));
    }

    @Override
    public VendorEntity getById(Long id) {
        VendorEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("供应商不存在");
        }
        return entity;
    }

    @Override
    public VendorEntity create(VendorEntity entity) {
        validateCode(entity.getCode(), null);
        if (entity.getIsActive() == null) entity.setIsActive(true);
        if (entity.getCreditLimit() == null) entity.setCreditLimit(java.math.BigDecimal.ZERO);
        if (entity.getCreditDays() == null) entity.setCreditDays(30);
        mapper.insert(entity);
        return entity;
    }

    @Override
    public VendorEntity update(VendorEntity entity) {
        VendorEntity existing = getById(entity.getId());
        validateCode(entity.getCode(), entity.getId());
        existing.setCode(entity.getCode());
        existing.setName(entity.getName());
        existing.setContactPerson(entity.getContactPerson());
        existing.setPhone(entity.getPhone());
        existing.setEmail(entity.getEmail());
        existing.setAddress(entity.getAddress());
        existing.setTaxNo(entity.getTaxNo());
        existing.setBankName(entity.getBankName());
        existing.setBankAccount(entity.getBankAccount());
        existing.setCreditLimit(entity.getCreditLimit());
        existing.setCreditDays(entity.getCreditDays());
        existing.setSubjectId(entity.getSubjectId());
        existing.setIsActive(entity.getIsActive());
        existing.setRemark(entity.getRemark());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> unsettledSummary() {
        return businessDocMapper.aggregateByVendor();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importFromExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".xlsx")) {
            throw BusinessException.badRequest("仅支持 .xlsx 格式文件");
        }

        List<ImportResult.ErrorItem> errors = new ArrayList<>();
        List<VendorEntity> validEntities = new ArrayList<>();

        // 加载数据库中已有的供应商编码，用于校验唯一性
        Set<String> existingCodes = new HashSet<>();
        mapper.selectList(new LambdaQueryWrapper<VendorEntity>().select(VendorEntity::getCode))
                .forEach(c -> existingCodes.add(c.getCode()));

        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw BusinessException.badRequest("Excel 中没有工作表");
            }

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum < 1) {
                // 只有表头，没有数据行
                ImportResult result = new ImportResult();
                result.setTotal(0);
                result.setSuccess(0);
                result.setErrors(errors);
                return result;
            }

            // 校验表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw BusinessException.badRequest("Excel 表头行为空");
            }
            validateHeader(headerRow);

            // 逐行解析数据（从第2行开始，行号从1开始）
            for (int rowIdx = 1; rowIdx <= lastRowNum; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                int excelRowNum = rowIdx + 1; // Excel 显示的行号（从1开始）

                try {
                    VendorEntity entity = parseRow(row, existingCodes);
                    if (entity != null) {
                        validEntities.add(entity);
                        existingCodes.add(entity.getCode());
                    }
                } catch (Exception e) {
                    errors.add(new ImportResult.ErrorItem(excelRowNum, e.getMessage()));
                    log.warn("导入 Excel 第 {} 行解析失败: {}", excelRowNum, e.getMessage());
                }
            }

            // 批量写入数据库
            if (!validEntities.isEmpty()) {
                for (VendorEntity entity : validEntities) {
                    mapper.insert(entity);
                }
                log.info("Excel 导入供应商完成，成功导入 {} 条", validEntities.size());
            }

        } catch (IOException e) {
            throw new BusinessException(500, "读取 Excel 文件失败: " + e.getMessage());
        }

        ImportResult result = new ImportResult();
        result.setTotal(validEntities.size() + errors.size());
        result.setSuccess(validEntities.size());
        result.setErrors(errors);
        return result;
    }

    @Override
    public InputStream createExportTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("供应商模板");

            // 创建表头样式（加粗，背景色浅蓝）
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // 创建普通单元格样式
            CellStyle bodyStyle = workbook.createCellStyle();
            bodyStyle.setBorderTop(BorderStyle.THIN);
            bodyStyle.setBorderBottom(BorderStyle.THIN);
            bodyStyle.setBorderLeft(BorderStyle.THIN);
            bodyStyle.setBorderRight(BorderStyle.THIN);

            // 写入表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 设置列宽
            sheet.setColumnWidth(COL_CODE, 4000);
            sheet.setColumnWidth(COL_NAME, 6000);
            sheet.setColumnWidth(COL_CONTACT, 4000);
            sheet.setColumnWidth(COL_PHONE, 4000);
            sheet.setColumnWidth(COL_EMAIL, 5000);
            sheet.setColumnWidth(COL_ADDRESS, 6000);
            sheet.setColumnWidth(COL_REMARK, 6000);

            // 写入一行示例数据
            Row exampleRow = sheet.createRow(1);
            String[] exampleData = {"V001", "示例供应商", "李四", "13900139000", "lisi@example.com", "上海市浦东新区", "重要供应商"};
            for (int i = 0; i < exampleData.length; i++) {
                Cell cell = exampleRow.createCell(i);
                cell.setCellValue(exampleData[i]);
                cell.setCellStyle(bodyStyle);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return new ByteArrayInputStream(baos.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(500, "生成模板文件失败: " + e.getMessage());
        }
    }

    private void validateCode(String code, Long excludeId) {
        LambdaQueryWrapper<VendorEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VendorEntity::getCode, code);
        if (excludeId != null) {
            wrapper.ne(VendorEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException("供应商编码已存在: " + code);
        }
    }

    // ===================== 私有方法 - Excel 导入 =====================

    /**
     * 校验表头与模板是否匹配
     */
    private void validateHeader(Row headerRow) {
        for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
            Cell cell = headerRow.getCell(i);
            String headerText = (cell != null) ? cell.toString().trim() : "";
            if (!TEMPLATE_HEADERS.get(i).equals(headerText)) {
                throw BusinessException.badRequest(
                        String.format("表头第 %d 列不匹配: 期望 '%s', 实际 '%s'",
                                i + 1, TEMPLATE_HEADERS.get(i), headerText));
            }
        }
    }

    /**
     * 解析单行数据
     */
    private VendorEntity parseRow(Row row, Set<String> existingCodes) {
        String code = getCellStringValue(row.getCell(COL_CODE));
        String name = getCellStringValue(row.getCell(COL_NAME));
        String contactPerson = getCellStringValue(row.getCell(COL_CONTACT));
        String phone = getCellStringValue(row.getCell(COL_PHONE));
        String email = getCellStringValue(row.getCell(COL_EMAIL));
        String address = getCellStringValue(row.getCell(COL_ADDRESS));
        String remark = getCellStringValue(row.getCell(COL_REMARK));

        // 校验供应商编码
        if (StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("供应商编码不能为空");
        }

        // 校验供应商编码唯一性
        if (existingCodes.contains(code)) {
            throw new IllegalArgumentException("供应商编码 '" + code + "' 已存在");
        }

        // 校验供应商名称
        if (StrUtil.isBlank(name)) {
            throw new IllegalArgumentException("供应商名称不能为空");
        }

        // 构建供应商对象
        VendorEntity entity = new VendorEntity();
        entity.setCode(code);
        entity.setName(name);
        entity.setContactPerson(StrUtil.isNotBlank(contactPerson) ? contactPerson : null);
        entity.setPhone(StrUtil.isNotBlank(phone) ? phone : null);
        entity.setEmail(StrUtil.isNotBlank(email) ? email : null);
        entity.setAddress(StrUtil.isNotBlank(address) ? address : null);
        entity.setRemark(StrUtil.isNotBlank(remark) ? remark : null);
        entity.setIsActive(true);

        return entity;
    }

    /**
     * 获取单元格的字符串值，处理 null 和空值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
}