-- P11-1: 员工档案表
CREATE TABLE IF NOT EXISTS t_employee (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(50),
    name          VARCHAR(100) NOT NULL,
    dept_id       BIGINT,
    phone         VARCHAR(20),
    email         VARCHAR(100),
    bank_name     VARCHAR(100),
    bank_account  VARCHAR(50),
    id_card       VARCHAR(18),
    is_active     BOOLEAN DEFAULT true,
    remark        VARCHAR(500),
    created_at    TIMESTAMP DEFAULT now(),
    updated_at    TIMESTAMP DEFAULT now(),
    deleted       INTEGER DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_t_employee_name ON t_employee(name);
CREATE INDEX IF NOT EXISTS idx_t_employee_code ON t_employee(code);
