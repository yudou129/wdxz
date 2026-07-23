# 数据查看审批 — 管辖逻辑修改记录

## 修改内容

`JwDataAccessServiceImpl.getReviewerDeptIds()` 方法重写。

## 旧逻辑

```java
// 通过 data_reviewer 角色的 sys_role_dept 配置获取管辖部门
for (SysRole role : user.getRoles()) {
    if ("data_reviewer".equals(role.getRoleKey())) {
        List<Long> deptIds = sysDeptMapper.selectDeptListByRoleId(role.getRoleId(), false);
        // 展开：管辖部门 + 所有子孙部门
        Set<Long> expanded = new HashSet<>(deptIds);
        for (Long deptId : deptIds) {
            List<SysDept> children = sysDeptMapper.selectChildrenDeptById(deptId);
            children.forEach(d -> expanded.add(d.getDeptId()));
        }
        return new ArrayList<>(expanded);
    }
}
```

**问题**：依赖 `sys_role_dept` 表来配置审批人管辖什么部门，不合理。

## 新逻辑

```java
// 审批人所在部门的直接子部门
SysUser user = sysUserMapper.selectUserById(userId);
if (user == null || user.getDeptId() == null) return Collections.emptyList();

SysDept query = new SysDept();
query.setParentId(user.getDeptId());
List<SysDept> children = sysDeptMapper.selectDeptList(query);
return children.stream().map(SysDept::getDeptId).collect(Collectors.toList());
```

**逻辑**：先查审批人属于哪个部门，然后查这个部门的**直接子部门**（只审下一级，不审更下层的）。

## 实际效果

| 审批人所在部门 | 管辖的部门 |
|---------------|-----------|
| 省行(201) | 贵阳分行(210)、遵义分行(220)、六盘水分行(230)... |
| 贵阳分行(210) | 清镇市支行(232)、乌当区支行(233)、云岩区支行(234)...共10个 |
| 遵义分行(220) | 遵义分行营业部(242)、红花岗区支行(243)...共5个 |
| 六盘水分行(230) | 六盘水分行营业部(254)、钟山区支行(255)、水城区支行(256)|

## 涉及文件

| 文件 | 路径 | 操作 |
|------|------|------|
| `JwDataAccessServiceImpl.java` | `jw-map/src/main/java/com/ruoyi/jwmap/service/impl/` | 第340-360行，重写 `getReviewerDeptIds()` 方法 |
