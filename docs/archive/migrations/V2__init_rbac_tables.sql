-- ============================================================
-- V2: 初始化RBAC权限相关表
-- 部门、用户、角色、菜单、用户角色关联、角色菜单关联、审计日志
-- ============================================================

-- 1. 部门表
CREATE TABLE IF NOT EXISTS t_dept (
    id          BIGINT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    parent_id   BIGINT,
    sort_order  INTEGER      NOT NULL DEFAULT 1,
    status      VARCHAR(20)  NOT NULL DEFAULT 'active',
    leader      VARCHAR(100),
    phone       VARCHAR(20),
    email       VARCHAR(100),
    created_by  BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_dept_status CHECK (status IN ('active', 'inactive'))
);

COMMENT ON TABLE  t_dept              IS '部门表';
COMMENT ON COLUMN t_dept.id           IS '主键';
COMMENT ON COLUMN t_dept.name         IS '部门名称';
COMMENT ON COLUMN t_dept.parent_id    IS '父部门ID';
COMMENT ON COLUMN t_dept.sort_order   IS '排序号';
COMMENT ON COLUMN t_dept.status       IS '状态: active-启用, inactive-停用';
COMMENT ON COLUMN t_dept.leader       IS '负责人';
COMMENT ON COLUMN t_dept.phone        IS '联系电话';
COMMENT ON COLUMN t_dept.email        IS '邮箱';
COMMENT ON COLUMN t_dept.created_by   IS '创建人';
COMMENT ON COLUMN t_dept.created_at   IS '创建时间';
COMMENT ON COLUMN t_dept.updated_by   IS '更新人';
COMMENT ON COLUMN t_dept.updated_at   IS '更新时间';
COMMENT ON COLUMN t_dept.deleted      IS '逻辑删除(0-未删,1-已删)';

-- 2. 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id              BIGINT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    real_name       VARCHAR(100),
    nickname        VARCHAR(100),
    email           VARCHAR(100),
    phone           VARCHAR(20),
    avatar          VARCHAR(500),
    dept_id         BIGINT REFERENCES t_dept(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    remark          VARCHAR(500),
    last_login_ip   VARCHAR(50),
    last_login_at   TIMESTAMP,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_username UNIQUE (username),
    CONSTRAINT chk_user_status CHECK (status IN ('active', 'inactive', 'locked'))
);

COMMENT ON TABLE  t_user                IS '用户表';
COMMENT ON COLUMN t_user.id             IS '主键';
COMMENT ON COLUMN t_user.username       IS '用户名';
COMMENT ON COLUMN t_user.password       IS '密码(BCrypt加密)';
COMMENT ON COLUMN t_user.real_name      IS '真实姓名';
COMMENT ON COLUMN t_user.nickname       IS '昵称';
COMMENT ON COLUMN t_user.email          IS '邮箱';
COMMENT ON COLUMN t_user.phone          IS '手机号';
COMMENT ON COLUMN t_user.avatar         IS '头像URL';
COMMENT ON COLUMN t_user.dept_id        IS '所属部门ID';
COMMENT ON COLUMN t_user.status         IS '状态: active-正常, inactive-停用, locked-锁定';
COMMENT ON COLUMN t_user.remark         IS '备注';
COMMENT ON COLUMN t_user.last_login_ip  IS '最后登录IP';
COMMENT ON COLUMN t_user.last_login_at  IS '最后登录时间';
COMMENT ON COLUMN t_user.created_by     IS '创建人';
COMMENT ON COLUMN t_user.created_at     IS '创建时间';
COMMENT ON COLUMN t_user.updated_by     IS '更新人';
COMMENT ON COLUMN t_user.updated_at     IS '更新时间';
COMMENT ON COLUMN t_user.deleted        IS '逻辑删除(0-未删,1-已删)';

-- 3. 角色表
CREATE TABLE IF NOT EXISTS t_role (
    id              BIGINT PRIMARY KEY,
    code            VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    sort_order      INTEGER      NOT NULL DEFAULT 1,
    data_scope      VARCHAR(20)  NOT NULL DEFAULT 'self',
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_code UNIQUE (code),
    CONSTRAINT chk_role_status CHECK (status IN ('active', 'inactive')),
    CONSTRAINT chk_role_data_scope CHECK (data_scope IN ('all', 'dept', 'self', 'custom'))
);

COMMENT ON TABLE  t_role                IS '角色表';
COMMENT ON COLUMN t_role.id             IS '主键';
COMMENT ON COLUMN t_role.code           IS '角色编码';
COMMENT ON COLUMN t_role.name           IS '角色名称';
COMMENT ON COLUMN t_role.description    IS '角色描述';
COMMENT ON COLUMN t_role.status         IS '状态: active-启用, inactive-停用';
COMMENT ON COLUMN t_role.sort_order     IS '排序号';
COMMENT ON COLUMN t_role.data_scope     IS '数据权限范围: all-全部, dept-本部门, self-仅本人, custom-自定义';
COMMENT ON COLUMN t_role.created_by     IS '创建人';
COMMENT ON COLUMN t_role.created_at     IS '创建时间';
COMMENT ON COLUMN t_role.updated_by     IS '更新人';
COMMENT ON COLUMN t_role.updated_at     IS '更新时间';
COMMENT ON COLUMN t_role.deleted        IS '逻辑删除(0-未删,1-已删)';

-- 4. 菜单/权限表
CREATE TABLE IF NOT EXISTS t_menu (
    id              BIGINT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    permission_code VARCHAR(100),
    type            VARCHAR(20)  NOT NULL DEFAULT 'menu',
    parent_id       BIGINT,
    path            VARCHAR(200),
    component       VARCHAR(200),
    icon            VARCHAR(100),
    sort_order      INTEGER      NOT NULL DEFAULT 1,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_visible      BOOLEAN      NOT NULL DEFAULT TRUE,
    keep_alive      BOOLEAN      NOT NULL DEFAULT TRUE,
    always_show     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_menu_type CHECK (type IN ('menu', 'button', 'iframe', 'external'))
);

COMMENT ON TABLE  t_menu                   IS '菜单/权限表';
COMMENT ON COLUMN t_menu.id                IS '主键';
COMMENT ON COLUMN t_menu.name              IS '菜单名称';
COMMENT ON COLUMN t_menu.permission_code   IS '权限标识(如system:user:query)';
COMMENT ON COLUMN t_menu.type              IS '类型: menu-菜单, button-按钮, iframe-内嵌iframe, external-外链';
COMMENT ON COLUMN t_menu.parent_id         IS '父菜单ID';
COMMENT ON COLUMN t_menu.path              IS '路由路径';
COMMENT ON COLUMN t_menu.component         IS '组件路径';
COMMENT ON COLUMN t_menu.icon              IS '图标';
COMMENT ON COLUMN t_menu.sort_order        IS '排序号';
COMMENT ON COLUMN t_menu.is_active         IS '是否启用';
COMMENT ON COLUMN t_menu.is_visible        IS '是否可见';
COMMENT ON COLUMN t_menu.keep_alive        IS '是否缓存';
COMMENT ON COLUMN t_menu.always_show       IS '是否始终显示';
COMMENT ON COLUMN t_menu.created_by        IS '创建人';
COMMENT ON COLUMN t_menu.created_at        IS '创建时间';
COMMENT ON COLUMN t_menu.updated_by        IS '更新人';
COMMENT ON COLUMN t_menu.updated_at        IS '更新时间';
COMMENT ON COLUMN t_menu.deleted           IS '逻辑删除(0-未删,1-已删)';

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS t_user_role (
    id          BIGINT PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES t_user(id),
    role_id     BIGINT NOT NULL REFERENCES t_role(id),
    CONSTRAINT uq_user_role UNIQUE (user_id, role_id)
);

COMMENT ON TABLE  t_user_role          IS '用户角色关联表';
COMMENT ON COLUMN t_user_role.id       IS '主键';
COMMENT ON COLUMN t_user_role.user_id  IS '用户ID';
COMMENT ON COLUMN t_user_role.role_id  IS '角色ID';

-- 6. 角色菜单关联表
CREATE TABLE IF NOT EXISTS t_role_menu (
    id          BIGINT PRIMARY KEY,
    role_id     BIGINT NOT NULL REFERENCES t_role(id),
    menu_id     BIGINT NOT NULL REFERENCES t_menu(id),
    CONSTRAINT uq_role_menu UNIQUE (role_id, menu_id)
);

COMMENT ON TABLE  t_role_menu          IS '角色菜单关联表';
COMMENT ON COLUMN t_role_menu.id       IS '主键';
COMMENT ON COLUMN t_role_menu.role_id  IS '角色ID';
COMMENT ON COLUMN t_role_menu.menu_id  IS '菜单ID';

-- 7. 审计日志表
CREATE TABLE IF NOT EXISTS t_audit_log (
    id                BIGINT PRIMARY KEY,
    user_id           BIGINT,
    username          VARCHAR(100),
    operation         VARCHAR(500),
    method            VARCHAR(500),
    request_params    JSONB,
    response_result   JSONB,
    old_snapshot      JSONB,
    new_snapshot      JSONB,
    ip_address        VARCHAR(50),
    user_agent        VARCHAR(500),
    execution_time_ms INTEGER,
    status            VARCHAR(20) NOT NULL DEFAULT 'success',
    module            VARCHAR(100),
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_audit_status CHECK (status IN ('success', 'fail'))
);

COMMENT ON TABLE  t_audit_log                  IS '审计日志表';
COMMENT ON COLUMN t_audit_log.id               IS '主键';
COMMENT ON COLUMN t_audit_log.user_id          IS '操作人ID';
COMMENT ON COLUMN t_audit_log.username         IS '操作人用户名';
COMMENT ON COLUMN t_audit_log.operation        IS '操作描述';
COMMENT ON COLUMN t_audit_log.method           IS '请求方法';
COMMENT ON COLUMN t_audit_log.request_params   IS '请求参数(JSONB)';
COMMENT ON COLUMN t_audit_log.response_result  IS '响应结果(JSONB)';
COMMENT ON COLUMN t_audit_log.old_snapshot     IS '修改前快照(JSONB)';
COMMENT ON COLUMN t_audit_log.new_snapshot     IS '修改后快照(JSONB)';
COMMENT ON COLUMN t_audit_log.ip_address       IS '请求IP';
COMMENT ON COLUMN t_audit_log.user_agent       IS '用户代理';
COMMENT ON COLUMN t_audit_log.execution_time_ms IS '执行耗时(ms)';
COMMENT ON COLUMN t_audit_log.status           IS '状态: success-成功, fail-失败';
COMMENT ON COLUMN t_audit_log.module           IS '所属模块';
COMMENT ON COLUMN t_audit_log.created_at       IS '创建时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_user_dept_id ON t_user(dept_id);
CREATE INDEX IF NOT EXISTS idx_user_username ON t_user(username);
CREATE INDEX IF NOT EXISTS idx_role_code ON t_role(code);
CREATE INDEX IF NOT EXISTS idx_menu_parent_id ON t_menu(parent_id);
CREATE INDEX IF NOT EXISTS idx_menu_permission_code ON t_menu(permission_code);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON t_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_module ON t_audit_log(module);

-- ============================================================
-- 初始数据
-- ============================================================

-- 初始部门
INSERT INTO t_dept (id, name, parent_id, sort_order) VALUES
(1, '慧财财务', NULL, 1)
ON CONFLICT (id) DO NOTHING;

-- 初始用户: admin / admin123 (BCrypt加密)
-- BCrypt hash for 'admin123' (generated with Spring Security BCryptPasswordEncoder)
INSERT INTO t_user (id, username, password, real_name, dept_id, status) VALUES
(1, 'admin', '$2a$10$zAmPUQlg0.cyO5OWh27VxuBP4cb1DOPrvj37qGLESp0auK38ZP1.a', '系统管理员', 1, 'active')
ON CONFLICT (username) DO NOTHING;

-- 初始角色
INSERT INTO t_role (id, code, name, description, data_scope) VALUES
(1, 'admin', '超级管理员', '系统超级管理员，拥有所有权限', 'all')
ON CONFLICT (code) DO NOTHING;

-- 关联admin用户到超级管理员角色
INSERT INTO t_user_role (id, user_id, role_id) VALUES
(1, 1, 1)
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================================
-- 初始菜单数据
-- ============================================================
-- 一级菜单: 系统管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(1, '系统管理', NULL, 'menu', NULL, '/system', 'Layout', 'Setting', 1)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 用户管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(10, '用户管理', 'system:user:list', 'menu', 1, '/system/user', 'system/user/UserList', 'User', 1)
ON CONFLICT (id) DO NOTHING;

-- 用户管理按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(101, '查询用户', 'system:user:query', 'button', 10, 1),
(102, '新增用户', 'system:user:create', 'button', 10, 2),
(103, '修改用户', 'system:user:update', 'button', 10, 3),
(104, '删除用户', 'system:user:delete', 'button', 10, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 角色管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(20, '角色管理', 'system:role:list', 'menu', 1, '/system/role', 'system/role/RoleList', 'Avatar', 2)
ON CONFLICT (id) DO NOTHING;

-- 角色管理按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(201, '查询角色', 'system:role:query', 'button', 20, 1),
(202, '新增角色', 'system:role:create', 'button', 20, 2),
(203, '修改角色', 'system:role:update', 'button', 20, 3),
(204, '删除角色', 'system:role:delete', 'button', 20, 4)
ON CONFLICT (id) DO NOTHING;

-- 二级菜单: 菜单管理
INSERT INTO t_menu (id, name, permission_code, type, parent_id, path, component, icon, sort_order) VALUES
(30, '菜单管理', 'system:menu:list', 'menu', 1, '/system/menu', 'system/menu/MenuList', 'Menu', 3)
ON CONFLICT (id) DO NOTHING;

-- 菜单管理按钮权限
INSERT INTO t_menu (id, name, permission_code, type, parent_id, sort_order) VALUES
(301, '查询菜单', 'system:menu:query', 'button', 30, 1),
(302, '新增菜单', 'system:menu:create', 'button', 30, 2),
(303, '修改菜单', 'system:menu:update', 'button', 30, 3),
(304, '删除菜单', 'system:menu:delete', 'button', 30, 4)
ON CONFLICT (id) DO NOTHING;

-- 关联超级管理员角色到所有菜单
-- 使用 menu_id 作为 id 的一部分避免 PK 冲突, 这样同一菜单只关联一次
INSERT INTO t_role_menu (id, role_id, menu_id)
SELECT id, 1, id FROM t_menu
ON CONFLICT (id) DO NOTHING;
