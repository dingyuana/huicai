CREATE TABLE t_reconciliation_tolerance (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    party_id BIGINT,
    party_type VARCHAR(20),
    tolerance_amount NUMERIC(18,2) DEFAULT 5.00,
    tolerance_rate NUMERIC(5,2) DEFAULT 10.00,
    effective_from DATE DEFAULT NOW(),
    effective_to DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted INTEGER DEFAULT 0
);

CREATE INDEX idx_tolerance_party ON t_reconciliation_tolerance(party_id, party_type);
CREATE INDEX idx_tolerance_tenant ON t_reconciliation_tolerance(tenant_id);

INSERT INTO t_reconciliation_tolerance (tenant_id, tolerance_amount, tolerance_rate)
VALUES (1, 5.00, 10.00);
