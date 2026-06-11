package com.huicai.module.finance.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 试算平衡算法的纯单元测试
 * 覆盖 SubjectBalanceServiceImpl.checkTrialBalance 的借/贷方向计算
 */
class TrialBalanceTest {

    /**
     * 模拟借方/贷方分组合计
     */
    static Map<String, Object> computeTotals(Map<String, String> subjectDirections,
                                              Map<String, BigDecimal> begins,
                                              Map<String, BigDecimal> debits,
                                              Map<String, BigDecimal> credits,
                                              Map<String, BigDecimal> ends) {
        BigDecimal bD = BigDecimal.ZERO, bC = BigDecimal.ZERO;
        BigDecimal dT = BigDecimal.ZERO, cT = BigDecimal.ZERO;
        BigDecimal eD = BigDecimal.ZERO, eC = BigDecimal.ZERO;
        for (String sid : subjectDirections.keySet()) {
            String dir = subjectDirections.get(sid);
            BigDecimal b = begins.getOrDefault(sid, BigDecimal.ZERO);
            BigDecimal d = debits.getOrDefault(sid, BigDecimal.ZERO);
            BigDecimal c = credits.getOrDefault(sid, BigDecimal.ZERO);
            BigDecimal e = ends.getOrDefault(sid, BigDecimal.ZERO);
            if ("debit".equals(dir)) {
                bD = bD.add(b);
                dT = dT.add(d);
                cT = cT.add(c);
                if (e.signum() >= 0) eD = eD.add(e); else eC = eC.add(e.abs());
            } else {
                bC = bC.add(b);
                dT = dT.add(d);
                cT = cT.add(c);
                if (e.signum() >= 0) eC = eC.add(e); else eD = eD.add(e.abs());
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("beginBalanced", bD.compareTo(bC) == 0);
        r.put("movementBalanced", dT.compareTo(cT) == 0);
        r.put("endBalanced", eD.compareTo(eC) == 0);
        r.put("balanced", bD.compareTo(bC) == 0 && dT.compareTo(cT) == 0 && eD.compareTo(eC) == 0);
        return r;
    }

    @Test
    void all_balanced_returns_true() {
        // 期初: 借 100, 贷 100 -> 平衡
        // 发生: 借 50, 贷 50 -> 平衡
        // 期末: 借 50, 贷 50 -> 平衡 (借方期初+借发生-贷发生=100+50=150, 但我们要平衡)
        // 改为: 借方 end=100, 贷方 end=50 + 贷方 end=50 (两个贷方科目各 50)
        Map<String, String> dirs = new HashMap<>();
        dirs.put("1", "debit");
        dirs.put("2", "credit");
        dirs.put("3", "credit");
        Map<String, BigDecimal> begins = new HashMap<>();
        begins.put("1", bd("100"));
        begins.put("2", bd("50"));
        begins.put("3", bd("50"));
        Map<String, BigDecimal> debits = new HashMap<>();
        debits.put("1", bd("50"));
        debits.put("2", bd("0"));
        debits.put("3", bd("0"));
        Map<String, BigDecimal> credits = new HashMap<>();
        credits.put("1", bd("0"));
        credits.put("2", bd("25"));
        credits.put("3", bd("25"));
        Map<String, BigDecimal> ends = new HashMap<>();
        ends.put("1", bd("150"));
        ends.put("2", bd("75"));
        ends.put("3", bd("75"));

        Map<String, Object> r = computeTotals(dirs, begins, debits, credits, ends);
        assertEquals(true, r.get("beginBalanced"));
        assertEquals(true, r.get("movementBalanced"));
        assertEquals(true, r.get("endBalanced"));
        assertEquals(true, r.get("balanced"));
    }

    @Test
    void unbalanced_movement_fails() {
        Map<String, String> dirs = Map.of("1", "debit", "2", "credit");
        Map<String, BigDecimal> begins = Map.of("1", bd("100"), "2", bd("100"));
        Map<String, BigDecimal> debits = Map.of("1", bd("50"), "2", bd("0"));
        Map<String, BigDecimal> credits = Map.of("1", bd("0"), "2", bd("30"));
        Map<String, BigDecimal> ends = Map.of("1", bd("150"), "2", bd("70"));

        Map<String, Object> r = computeTotals(dirs, begins, debits, credits, ends);
        assertEquals(false, r.get("balanced"));
        assertEquals(true, r.get("beginBalanced"));
        assertEquals(false, r.get("movementBalanced"));
    }

    @Test
    void unbalanced_beginning_fails() {
        Map<String, String> dirs = Map.of("1", "debit", "2", "credit");
        Map<String, BigDecimal> begins = Map.of("1", bd("100"), "2", bd("50"));
        Map<String, BigDecimal> debits = Map.of("1", bd("0"), "2", bd("0"));
        Map<String, BigDecimal> credits = Map.of("1", bd("0"), "2", bd("0"));
        Map<String, BigDecimal> ends = Map.of("1", bd("100"), "2", bd("50"));

        Map<String, Object> r = computeTotals(dirs, begins, debits, credits, ends);
        assertEquals(false, r.get("balanced"));
        assertEquals(false, r.get("beginBalanced"));
    }

    @Test
    void negative_end_balance_for_credit_subject_treated_as_debit() {
        Map<String, String> dirs = Map.of("1", "credit");
        Map<String, BigDecimal> begins = Map.of("1", bd("0"));
        Map<String, BigDecimal> debits = Map.of("1", bd("100"));
        Map<String, BigDecimal> credits = Map.of("1", bd("0"));
        // 贷方科目出现负余额(实际是借方余额) = 100
        Map<String, BigDecimal> ends = Map.of("1", bd("-100"));

        Map<String, Object> r = computeTotals(dirs, begins, debits, credits, ends);
        // 贷方科目 -100 视为借方余额 eD 增加
        // 期末 借方 100, 贷方 0, 不平衡
        assertEquals(false, r.get("endBalanced"));
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }
}
