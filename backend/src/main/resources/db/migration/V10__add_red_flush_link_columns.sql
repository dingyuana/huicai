ALTER TABLE t_output_invoice ADD COLUMN reversed_by_invoice_id BIGINT;
ALTER TABLE t_output_invoice ADD COLUMN original_invoice_no VARCHAR(64);

ALTER TABLE t_output_invoice ADD CONSTRAINT fk_output_invoice_reversed_by FOREIGN KEY (reversed_by_invoice_id) REFERENCES t_output_invoice(id);

CREATE INDEX idx_output_invoice_original_no ON t_output_invoice(original_invoice_no);
CREATE INDEX idx_output_invoice_reversed_by ON t_output_invoice(reversed_by_invoice_id);