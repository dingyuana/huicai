INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3064, '核销工作台', 'arap:reconciliation:workbench', 'menu', 3000, '/arap/reconciliation-workbench', 'arap/reconciliation-workbench/ReconciliationWorkbench', 'Workbench', 7, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

DO $$
DECLARE
    v_next_id BIGINT;
    v_role_id BIGINT;
    v_menu_id BIGINT;
BEGIN
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM t_role_menu;
    SELECT id INTO v_role_id FROM t_role WHERE code = 'admin';
    FOR v_menu_id IN 
        SELECT m.id FROM t_menu m
        WHERE m.id IN (3064)
        AND m.id NOT IN (SELECT menu_id FROM t_role_menu WHERE role_id = v_role_id)
    LOOP
        INSERT INTO t_role_menu (id, role_id, menu_id) VALUES (v_next_id, v_role_id, v_menu_id);
        v_next_id := v_next_id + 1;
    END LOOP;
END $$;