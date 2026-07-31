ALTER TABLE kp_proposals
    ADD COLUMN drone_vat_pct INTEGER NOT NULL DEFAULT 0;

ALTER TABLE kp_proposals
    ADD CONSTRAINT chk_kp_proposals_drone_vat_pct CHECK (drone_vat_pct IN (0, 22));
