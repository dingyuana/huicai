-- ============================================================
-- V16: P0-P2 新增模块菜单权限注册
-- 期初建账 / 现金日记账 / 票据管理 / 往来核销 / 结转向导
-- ============================================================

-- 1. 现金日记账 (插在银行日记账之后, sort=6→改银行对账单到7)
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1090, '现金日记账', 'cash:journal:list', 'menu', 1000, '/finance/cash-journal', 'finance/cash-journal/CashJournalList', 'Coin', 5, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1091, '查询日记账', 'cash:journal:query', 'button', 1090, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1092, '新增日记账', 'cash:journal:create', 'button', 1090, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1093, '删除日记账', 'cash:journal:delete', 'button', 1090, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1094, '生成凭证', 'cash:journal:voucher', 'button', 1090, 4)
ON CONFLICT (id) DO NOTHING;

-- 2. 票据管理 (插在银行对账之后)
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1095, '票据管理', 'ticket:list', 'menu', 1000, '/finance/ticket', 'finance/ticket/TicketList', 'Ticket', 9, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1096, '查询票据', 'ticket:query', 'button', 1095, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1097, '新增票据', 'ticket:create', 'button', 1095, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1098, '领用票据', 'ticket:issue', 'button', 1095, 3)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1099, '作废票据', 'ticket:void', 'button', 1095, 4)
ON CONFLICT (id) DO NOTHING;

-- 3. 期初建账 (插在财务核心下)
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1100, '期初建账', 'beginning:balance:init', 'menu', 1000, '/finance/beginning-balance', 'finance/beginning-balance/BeginningBalanceView', 'DataBoard', 10, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1101, '录入期初', 'beginning:balance:enter', 'button', 1100, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1102, '试算平衡', 'beginning:balance:trial', 'button', 1100, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (1103, '锁定期初', 'beginning:balance:lock', 'button', 1100, 3)
ON CONFLICT (id) DO NOTHING;

-- 4. 往来核销 (插在坏账准备之后)
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (3060, '往来核销', 'arap:settlement:list', 'menu', 3000, '/arap/settlement', 'arap/settlement/SettlementList', 'SetUp', 6, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (3061, '查询核销', 'arap:settlement:query', 'button', 3060, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (3062, '新增核销', 'arap:settlement:create', 'button', 3060, 2)
ON CONFLICT (id) DO NOTHING;
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order)
VALUES (3063, '确认核销', 'arap:settlement:confirm', 'button', 3060, 3)
ON CONFLICT (id) DO NOTHING;

-- 5. 结转向导 (插在期末结账后)
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order, is_active, is_visible)
VALUES (1033, '结转向导', 'period:carryover:guide', 'menu', 1000, '/finance/carryover-guide', 'finance/period-close/CarryOverGuide', 'Guide', 11, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

-- 6. 授权给 admin 角色
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
        WHERE m.id IN (1090,1091,1092,1093,1094,1095,1096,1097,1098,1099,1100,1101,1102,1103,3060,3061,3062,3063,1033)
        AND m.id NOT IN (SELECT menu_id FROM t_role_menu WHERE role_id = v_role_id)
    LOOP
        INSERT INTO t_role_menu (id, role_id, menu_id) VALUES (v_next_id, v_role_id, v_menu_id);
        v_next_id := v_next_id + 1;
    END LOOP;
END $$;