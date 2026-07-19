package com.huicai.sme.cash.constant;

/**
 * 银行流水业务分类常量 (8类体系).
 */
public final class BankClassification {

    private BankClassification() {}

    public static final String BANK_INTEREST_FEE = "bank_interest_fee";
    public static final String TAX_WITHHOLDING = "tax_withholding";
    public static final String SALARY_SOCIAL = "salary_social";
    public static final String BUSINESS_RECEIPT = "business_receipt";
    public static final String BUSINESS_PAYMENT = "business_payment";
    public static final String INTERNAL_TRANSFER = "internal_transfer";
    public static final String FINANCING_INVEST = "financing_invest";
    public static final String OTHER_UNKNOWN = "other_unknown";

    public static String routeType(String classification) {
        return switch (classification) {
            case BANK_INTEREST_FEE, TAX_WITHHOLDING -> "A";
            case BUSINESS_RECEIPT, BUSINESS_PAYMENT,
                 INTERNAL_TRANSFER, SALARY_SOCIAL -> "B";
            case FINANCING_INVEST, OTHER_UNKNOWN -> "C";
            default -> "C";
        };
    }
}
