package com.huicai.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AmountExpressionResolver 单元测试.
 */
class AmountExpressionResolverTest {

    @Test
    void evaluate_加法() {
        assertEquals(0, new BigDecimal("3.00").compareTo(AmountExpressionResolver.evaluate("1+2")));
    }

    @Test
    void evaluate_减法() {
        assertEquals(0, new BigDecimal("5.00").compareTo(AmountExpressionResolver.evaluate("10-5")));
    }

    @Test
    void evaluate_乘法() {
        assertEquals(0, new BigDecimal("6.00").compareTo(AmountExpressionResolver.evaluate("2*3")));
    }

    @Test
    void evaluate_除法() {
        assertEquals(0, new BigDecimal("5.00").compareTo(AmountExpressionResolver.evaluate("10/2")));
    }

    @Test
    void evaluate_复合运算() {
        assertEquals(0, new BigDecimal("7.00").compareTo(AmountExpressionResolver.evaluate("1+2*3")));
    }

    @Test
    void evaluate_括号() {
        assertEquals(0, new BigDecimal("9.00").compareTo(AmountExpressionResolver.evaluate("(1+2)*3")));
    }

    @Test
    void evaluate_小数() {
        assertEquals(0, new BigDecimal("0.50").compareTo(AmountExpressionResolver.evaluate("3.5-3")));
    }

    @Test
    void evaluate_空返回零() {
        assertEquals(0, BigDecimal.ZERO.compareTo(AmountExpressionResolver.evaluate("")));
    }

    @Test
    void evaluate_非法表达式抛出异常() {
        assertThrows(IllegalArgumentException.class, () -> AmountExpressionResolver.evaluate("1+abc"));
        assertThrows(IllegalArgumentException.class, () -> AmountExpressionResolver.evaluate("System.exit(0)"));
    }

    @Test
    void evaluate_结果保留两位小数() {
        assertEquals(0, new BigDecimal("3.33").compareTo(AmountExpressionResolver.evaluate("10/3")));
    }

    @Test
    void evaluateTemplate_替换变量() {
        Map<String, BigDecimal> vars = new HashMap<>();
        vars.put("amount", new BigDecimal("1000"));
        vars.put("tax", new BigDecimal("100"));
        assertEquals(0, new BigDecimal("900.00").compareTo(
                AmountExpressionResolver.evaluateTemplate("{{amount}}-{{tax}}", vars)));
    }

    @Test
    void evaluateTemplate_变量不存在返回零() {
        Map<String, BigDecimal> vars = new HashMap<>();
        assertEquals(0, new BigDecimal("10.00").compareTo(
                AmountExpressionResolver.evaluateTemplate("10", vars)));
    }
}