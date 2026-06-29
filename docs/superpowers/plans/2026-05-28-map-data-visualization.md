# 地图数据展示功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在天地图离线瓦片地图上实现网格热力展示、网点标注、侧边栏详情、排名筛选等数据可视化功能

**Architecture:** 后端新增 6 个查询接口（网格得分 API 需 JOIN jw_grid_meta 返回坐标），前端在 tianditu.vue 基础上新增 6 个 Vue 组件 + 1 个 JS 工具类，所有组件浮动覆盖在地图上

**Tech Stack:** Spring Boot 2.5 + MyBatis / Vue 2 + Element UI + Leaflet 1.7.1

---

## 关键设计决策

1. **网格得分 API 返回坐标** — `JwGridScore` 本身不含坐标字段，`/grid/score/byCity` 通过 JOIN `jw_grid_meta` 返回 gridCode + siteScore + 经纬度 + 包围盒。使用 HashMap 返回而非修改 domain 类
2. **网格排名** — `JwGridScore` 没有 `rank_num` 字段，前端从有序数组的 index+1 计算排名（API 已 ORDER BY site_score DESC）
3. **子组件拆分** — ScoreCard / IndicatorSection / BranchScores 拆为独立 .vue 文件，而非内嵌

## 文件结构

### 后端
| 文件 | 操作 | 说明 |
|---|---|---|
| `jw-map/.../controller/JwDataController.java` | 修改 | +6 个 GET 接口（grid/score 返回合并坐标） |
| `jw-map/.../mapper/JwBranchInfoMapper.java` | 修改 | +1 方法 selectByGridCode |
| `jw-map/.../mapper/jwmap/JwBranchInfoMapper.xml` | 修改 | +1 SQL selectByGridCode |

现有 Mapper 可直接复用：
- `JwGridScoreMapper.selectByCity` → grid/ranking
- `JwGridDataRawMapper.selectByGridCode` → grid/indicators
- `JwBranchScoreMapper.selectByBranchAndYear` → branch/score/detail
- `JwBranchScoreMapper.selectByCityAndYearAndCategory` → branch/ranking

### 前端
| 文件 | 操作 | 说明 |
|---|---|---|
| `ruoyi-ui/src/api/jwmap/data.js` | 修改 | +6 个 API 函数 |
| `ruoyi-ui/src/views/jwmap/map/tianditu.vue` | 修改 | 集成所有子组件 |
| `components/TopToolbar.vue` | 新建 | 顶部工具栏 |
| `components/SidebarPanel.vue` | 新建 | 浮动侧边栏主容器 |
| `components/ScoreCard.vue` | 新建 | 得分卡片子组件 |
| `components/IndicatorSection.vue` | 新建 | 分项指标列表子组件 |
| `components/BranchScores.vue` | 新建 | 网点分项得分进度条 |
| `components/RankingList.vue` | 新建 | 排名列表 |
| `utils/heatmapLayer.js` | 新建 | Canvas 热力图层 |
| `assets/branch-icon.css` | 新建 | 网点图标样式 |

---

## Task 1: 拷贝 GeoJSON 边界数据

- [ ] **拷贝边界文件**

```bash
cp -r /e/coding/wangdianxuanzhi/map/src/data/map_data_bd09/*.json \
      ruoyi-ui/public/data/map_data/
```

Expected: `ls ruoyi-ui/public/data/map_data/` 显示 10 个 `*_full.json` 文件

- [ ] **提交**

```bash
git add ruoyi-ui/public/data/map_data/
git commit -m "feat: add GeoJSON boundary data files"
```

---

## Task 2: 后端新增 API — JwDataController

**Files:**
- Modify: `jw-map/src/main/java/com/ruoyi/jwmap/controller/JwDataController.java`
- Modify: `jw-map/src/main/java/com/ruoyi/jwmap/mapper/JwBranchInfoMapper.java` (+selectByGridCode)
- Modify: `jw-map/src/main/resources/mapper/jwmap/JwBranchInfoMapper.xml` (+SQL)

### 关键说明

`JwGridScore` domain 只有 gridCode, city, siteScore, 没有坐标字段。接口 1 `/grid/score/byCity` 必须 JOIN `jw_grid_meta` 返回坐标。使用 `HashMap` 构造响应。

- [ ] **Step 1: 给 JwBranchInfoMapper 添加 selectByGridCode 方法**

```java
// JwBranchInfoMapper.java 追加
List<JwBranchInfo> selectByGridCode(@Param("gridCode") String gridCode);
```

- [ ] **Step 2: 给 JwBranchInfoMapper.xml 添加 SQL**

```xml
<!-- 追加到 selectByCity 后面 -->
<select id="selectByGridCode" parameterType="String" resultMap="JwBranchInfoResult">
    <include refid="selectJwBranchInfoVo"/>
    WHERE grid_code = #{gridCode} AND (del_flag IS NULL OR del_flag = '0')
    ORDER BY branch_id
</select>
```

- [ ] **Step 3: 在 JwDataController 注入新依赖**

```java
// 已有成员变量下面追加
@Autowired private JwGridDataRawMapper gridDataRawMapper;
```

- [ ] **Step 4: 添加 6 个新接口**

找 `JwDataController.java` 中 `// ===== 网格 =====` 区块，在 `gridCities` 之后追加：

**接口 1: 网格得分列表（含坐标 — JOIN jw_grid_meta）**
```java
@GetMapping("/grid/score/byCity/{city}")
public AjaxResult gridScoreByCity(@PathVariable String city) {
    List<JwGridMeta> metas = gridMetaMapper.selectByCity(city);
    List<Map<String, Object>> result = new ArrayList<>();
    for (JwGridMeta meta : metas) {
        JwGridScore score = gridScoreMapper.selectByGridCode(meta.getGridCode());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("gridCode", meta.getGridCode());
        item.put("longitude", meta.getLongitude());
        item.put("latitude", meta.getLatitude());
        item.put("westLongitude", meta.getWestLongitude());
        item.put("eastLongitude", meta.getEastLongitude());
        item.put("northLatitude", meta.getNorthLatitude());
        item.put("southLatitude", meta.getSouthLatitude());
        item.put("siteScore", score != null ? score.getSiteScore() : null);
        result.add(item);
    }
    // siteScore 降序排列，为前端提供排名依据
    result.sort((a, b) -> {
        Double sa = (Double) a.get("siteScore");
        Double sb = (Double) b.get("siteScore");
        if (sa == null) return 1;
        if (sb == null) return -1;
        return sb.compareTo(sa);
    });
    return success(result);
}
```

**接口 2: 网格分项指标**
```java
@GetMapping("/grid/indicators/{gridCode}")
public AjaxResult gridIndicators(@PathVariable String gridCode) {
    List<JwGridDataRaw> list = gridDataRawMapper.selectByGridCode(gridCode);
    return success(list);
}
```

**接口 3: 网点分项得分**
```java
@GetMapping("/branch/score/detail/{branchId}/{year}")
public AjaxResult branchScoreDetail(@PathVariable Long branchId, @PathVariable Integer year) {
    List<JwBranchScore> list = branchScoreMapper.selectByBranchAndYear(branchId, year);
    return success(list);
}
```

**接口 4: 网格排名（分页）**
```java
@GetMapping("/grid/ranking/{city}")
public TableDataInfo gridRanking(@PathVariable String city) {
    startPage();
    List<JwGridScore> list = gridScoreMapper.selectByCity(city);
    return getDataTable(list);
}
```

**接口 5: 网点排名（分页）**
```java
@GetMapping("/branch/ranking/{city}/{year}")
public TableDataInfo branchRanking(@PathVariable String city, @PathVariable Integer year) {
    startPage();
    List<JwBranchScore> list = branchScoreMapper.selectByCityAndYearAndCategory(city, year, "overall");
    return getDataTable(list);
}
```

**接口 6: 网格内网点列表**
```java
@GetMapping("/grid/branches/{gridCode}")
public AjaxResult gridBranches(@PathVariable String gridCode) {
    List<JwBranchInfo> list = branchInfoMapper.selectByGridCode(gridCode);
    return success(list);
}
```

- [ ] **Step 5: 编译验证**

```bash
cd ruoyi-admin && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add jw-map/src/main/java/com/ruoyi/jwmap/controller/JwDataController.java
git add jw-map/src/main/java/com/ruoyi/jwmap/mapper/JwBranchInfoMapper.java
git add jw-map/src/main/resources/mapper/jwmap/JwBranchInfoMapper.xml
git commit -m "feat: add 6 data query APIs for map visualization"
```

---

## Task 3: 前端 data.js 新增 API 函数

**Files:**
- Modify: `ruoyi-ui/src/api/jwmap/data.js`

- [ ] **添加 6 个 API 函数**

在文件末尾 `getBranchEfficiencyWeightList` 之后追加：

```javascript
// ===== 地图可视化 =====
export function getGridScoreByCity(city) {
  return request({ url: '/jwmap/data/grid/score/byCity/' + city, method: 'get' })
}
export function getGridIndicators(gridCode) {
  return request({ url: '/jwmap/data/grid/indicators/' + gridCode, method: 'get' })
}
export function getBranchScoreDetail(branchId, year) {
  return request({ url: '/jwmap/data/branch/score/detail/' + branchId + '/' + year, method: 'get' })
}
export function getGridRanking(city) {
  return request({ url: '/jwmap/data/grid/ranking/' + city, method: 'get' })
}
export function getBranchRanking(city, year) {
  return request({ url: '/jwmap/data/branch/ranking/' + city + '/' + year, method: 'get' })
}
export function getGridBranches(gridCode) {
  return request({ url: '/jwmap/data/grid/branches/' + gridCode, method: 'get' })
}
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/api/jwmap/data.js
git commit -m "feat: add map visualization API calls"
```

---

## Task 4: 网点图标样式

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/assets/branch-icon.css`

- [ ] **创建图标 CSS**

```css
.branch-icon {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #d40000;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  font-family: 'SimHei', sans-serif;
  border: 2px solid #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.3);
  cursor: pointer;
}
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/assets/branch-icon.css
git commit -m "feat: add branch marker icon style"
```

---

## Task 5: 顶部工具栏组件 TopToolbar

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/components/TopToolbar.vue`

关键注意：`BoundaryManager` 中的 cities 是 GeoJSON Feature 数组，属性在 `c.properties.adcode` 和 `c.properties.name`。

- [ ] **创建 TopToolbar.vue**

```vue
<template>
  <div class="top-toolbar">
    <span class="toolbar-label">行政区：</span>
    <el-select v-model="selectedCity" size="small" placeholder="贵州省" @change="onCityChange">
      <el-option label="贵州省" value="all" />
      <!-- cities 是 GeoJSON Feature 数组，属性在 c.properties 下 -->
      <el-option v-for="c in cities" :key="c.properties.adcode"
                 :label="c.properties.name" :value="c.properties.adcode" />
    </el-select>

    <span class="toolbar-label" style="margin-left:12px;">一级支行：</span>
    <el-select v-model="selectedBranch" size="small" placeholder="全部" @change="onBranchChange">
      <el-option label="全部" value="all" />
      <el-option v-for="b in branchOptions" :key="b" :label="b" :value="b" />
    </el-select>

    <div class="toolbar-right">
      <el-button size="small" :type="heatmapActive ? 'danger' : 'default'"
                 icon="el-icon-s-data" @click="$emit('toggle-heatmap')">
        热力图
      </el-button>
    </div>
  </div>
</template>

<script>
import { getBranchList } from '@/api/jwmap/data'

export default {
  name: 'TopToolbar',
  props: {
    cities: { type: Array, default: () => [] },
    heatmapActive: { type: Boolean, default: false }
  },
  data() {
    return {
      selectedCity: 'all',
      selectedBranch: 'all',
      branchOptions: []
    }
  },
  methods: {
    onCityChange(val) {
      if (val === 'all') {
        this.branchOptions = []
        this.selectedBranch = 'all'
        this.$emit('select-city', null)
      } else {
        this.loadBranches(val)
        this.$emit('select-city', val)
      }
    },
    async loadBranches(city) {
      try {
        const res = await getBranchList(city)
        const branches = [...new Set((res.data || []).map(b => b.primaryBranch).filter(Boolean))]
        this.branchOptions = branches.sort()
      } catch (e) {
        this.branchOptions = []
      }
    },
    onBranchChange(val) {
      this.$emit('filter-branch', val === 'all' ? null : val)
    }
  }
}
</script>

<style scoped>
.top-toolbar {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: rgba(255,255,255,0.95);
  padding: 8px 16px;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  display: flex;
  align-items: center;
  white-space: nowrap;
}
.toolbar-label { font-size: 13px; color: #555; }
.toolbar-right { margin-left: 24px; }
</style>
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/components/TopToolbar.vue
git commit -m "feat: add TopToolbar component with city/branch selectors"
```

---

## Task 6: 热力图模块 heatmapLayer.js

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/utils/heatmapLayer.js`

关键说明：`/grid/score/byCity` 返回的数据已包含 `longitude, latitude, westLongitude, eastLongitude, northLatitude, southLatitude, siteScore`。

- [ ] **创建 heatmapLayer.js**

```javascript
import L from 'leaflet'

/**
 * 热力图层 — Canvas 渲染 + L.rectangle 点击热区
 * 配色：low(0) #00ff00 → #ffff00 → #ff0000 high(1)
 * 数据格式：{ gridCode, longitude, latitude, westLongitude, eastLongitude,
 *             northLatitude, southLatitude, siteScore }
 */
export class HeatmapLayer {
  constructor(map) {
    this.map = map
    this._canvasLayer = null
    this._clickLayer = L.layerGroup()
    this._data = []
    this._visible = false
  }

  isVisible() { return this._visible }

  async loadData(city) {
    const { getGridScoreByCity } = await import('@/api/jwmap/data')
    const res = await getGridScoreByCity(city)
    this._data = res.data || []
    return this._data
  }

  show() {
    if (this._visible) return
    this._visible = true
    this._renderCanvas()
    this._renderClickRects()
    if (this._canvasLayer) this.map.addLayer(this._canvasLayer)
    if (this._clickLayer) this.map.addLayer(this._clickLayer)
  }

  hide() {
    if (!this._visible) return
    this._visible = false
    if (this._canvasLayer) { this.map.removeLayer(this._canvasLayer); this._canvasLayer = null }
    if (this._clickLayer) { this.map.removeLayer(this._clickLayer); this._clickLayer.clearLayers() }
  }

  _renderCanvas() {
    const self = this
    this._canvasLayer = L.gridLayer({
      tileSize: 512,
      minZoom: this.map.getMinZoom(),
      maxZoom: this.map.getMaxZoom()
    })
    this._canvasLayer.createTile = function (coords) {
      const tile = L.DomUtil.create('canvas', 'leaflet-tile')
      tile.width = 512
      tile.height = 512
      tile.style.width = '512px'
      tile.style.height = '512px'
      const ctx = tile.getContext('2d')
      const zoom = coords.z
      const tileSize = 512
      const tileX = coords.x
      const tileY = coords.y
      const crs = self.map.options.crs

      for (const item of self._data) {
        const score = item.siteScore
        if (score == null) continue
        const center = crs.latLngToPoint(L.latLng(item.latitude, item.longitude), zoom)
        const px = center.x - tileX * tileSize
        const py = center.y - tileY * tileSize
        if (px < -100 || px > 612 || py < -100 || py > 612) continue
        const radius = 24
        const color = self._scoreToColor(score)
        const gradient = ctx.createRadialGradient(px, py, 0, px, py, radius)
        gradient.addColorStop(0, color)
        gradient.addColorStop(0.4, color.replace('1)', '0.5)'))
        gradient.addColorStop(1, 'rgba(0,0,0,0)')
        ctx.fillStyle = gradient
        ctx.beginPath()
        ctx.arc(px, py, radius, 0, Math.PI * 2)
        ctx.fill()
      }
      return tile
    }
  }

  _renderClickRects() {
    this._clickLayer.clearLayers()
    for (const item of this._data) {
      if (item.siteScore == null) continue
      const rect = L.rectangle(
        [[item.southLatitude, item.westLongitude], [item.northLatitude, item.eastLongitude]],
        { color: 'transparent', weight: 0, fillColor: 'transparent', fillOpacity: 0 }
      )
      rect.on('click', () => {
        this.map.fire('grid-click', { gridCode: item.gridCode, data: item })
      })
      this._clickLayer.addLayer(rect)
    }
  }

  _scoreToColor(score) {
    const t = Math.max(0, Math.min(1, score))
    if (t < 0.5) {
      const s = t / 0.5
      return `rgba(${Math.round(255 * s)}, 255, 0, 0.8)`
    } else {
      const s = (t - 0.5) / 0.5
      return `rgba(255, ${Math.round(255 * (1 - s))}, 0, 0.8)`
    }
  }
}
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/utils/heatmapLayer.js
git commit -m "feat: add Canvas heatmap layer with click interaction"
```

---

## Task 7: 侧边栏组件 — 子组件

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/components/ScoreCard.vue`
- Create: `ruoyi-ui/src/views/jwmap/map/components/IndicatorSection.vue`
- Create: `ruoyi-ui/src/views/jwmap/map/components/BranchScores.vue`

- [ ] **创建 ScoreCard.vue**

```vue
<template>
  <div class="score-card" :class="size">
    <div class="score-label">{{ label }}</div>
    <div class="score-value">{{ typeof score === 'number' ? score.toFixed(2) : '-' }}</div>
    <div class="score-rank" v-if="rank != null">全市排名：#{{ rank }}</div>
  </div>
</template>
<script>
export default {
  name: 'ScoreCard',
  props: { score: Number, rank: Number, label: String, size: { type: String, default: 'normal' } }
}
</script>
<style scoped>
.score-card { background: #f6ffed; border: 1px solid #b7eb8f; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.score-card.small { padding: 8px 12px; }
.score-label { font-size: 12px; color: #888; margin-bottom: 2px; }
.score-value { font-size: 28px; font-weight: 700; color: #52c41a; }
.score-card.small .score-value { font-size: 22px; }
.score-rank { font-size: 12px; color: #888; margin-top: 2px; }
</style>
```

- [ ] **创建 IndicatorSection.vue**

```vue
<template>
  <div class="indicator-section">
    <h4 v-if="title" class="indicator-title">{{ title }}</h4>
    <div v-for="item in items" :key="item.code" class="indicator-row" :class="{ compact }">
      <span class="indicator-name">{{ item.name }}</span>
      <span class="indicator-value">{{ item.value }}</span>
    </div>
  </div>
</template>
<script>
export default {
  name: 'IndicatorSection',
  props: { title: String, items: Array, compact: Boolean }
}
</script>
<style scoped>
.indicator-title { font-size: 13px; font-weight: 600; color: #555; margin: 12px 0 8px; }
.indicator-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 13px; }
.indicator-row.compact { font-size: 12px; padding: 2px 0; }
.indicator-name { color: #666; }
.indicator-value { color: #333; font-weight: 500; }
</style>
```

- [ ] **创建 BranchScores.vue**

```vue
<template>
  <div class="branch-scores">
    <div v-for="s in scores" :key="s.category || s.scoreCategory" class="score-row" :class="{ compact }">
      <span class="s-label">{{ categoryLabel(s.category || s.scoreCategory) }}</span>
      <div class="s-bar-bg">
        <div class="s-bar" :style="{ width: ((s.categoryScore || s.score) * 100) + '%', background: scoreColor(s.categoryScore || s.score) }" />
      </div>
      <span class="s-value">{{ (s.categoryScore || s.score).toFixed(2) }}</span>
    </div>
  </div>
</template>
<script>
export default {
  name: 'BranchScores',
  props: { scores: Array, compact: Boolean },
  methods: {
    categoryLabel(c) {
      return { revenue: '营收', indicator: '业绩', customer: '客户', operation: '运营', overall: '综合' }[c] || c
    },
    scoreColor(s) {
      return s >= 0.8 ? '#52c41a' : s >= 0.6 ? '#faad14' : '#f5222d'
    }
  }
}
</script>
<style scoped>
.score-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; font-size: 13px; }
.score-row.compact { font-size: 12px; padding: 3px 0; }
.s-label { width: 36px; color: #666; }
.s-bar-bg { flex: 1; height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.s-bar { height: 100%; border-radius: 4px; transition: width 0.3s; }
.s-value { width: 44px; text-align: right; color: #333; font-weight: 500; }
</style>
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/components/ScoreCard.vue
git add ruoyi-ui/src/views/jwmap/map/components/IndicatorSection.vue
git add ruoyi-ui/src/views/jwmap/map/components/BranchScores.vue
git commit -m "feat: add sidebar sub-components (ScoreCard, IndicatorSection, BranchScores)"
```

---

## Task 8: 侧边栏主容器 SidebarPanel

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/components/SidebarPanel.vue`

- [ ] **创建 SidebarPanel.vue**

```vue
<template>
  <transition name="slide">
    <div v-if="visible" class="sidebar-panel" :style="{ width: width + 'px' }">
      <div class="sidebar-header">
        <span class="sidebar-title">{{ title }}</span>
        <el-button type="text" icon="el-icon-close" @click="$emit('close')" />
      </div>
      <div class="sidebar-body">
        <!-- 年份选择器：仅网点模式下显示 -->
        <div v-if="showYearPicker" class="year-picker">
          <span class="label">年份：</span>
          <el-select :value="year" size="mini" @change="$emit('year-change', $event)">
            <el-option v-for="y in years" :key="y" :label="y" :value="y" />
          </el-select>
        </div>

        <!-- 纯网格模式 -->
        <template v-if="mode === 'grid-only'">
          <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" />
          <IndicatorSection title="分项指标" :items="gridIndicators" />
          <div class="info-section">
            <p>网格编码：{{ gridData.gridCode }}</p>
            <p>所属区县：{{ gridData.district || '-' }}</p>
            <el-button type="text" @click="$emit('view-detail', 'grid')">查看详情 →</el-button>
          </div>
        </template>

        <!-- 纯网点模式 -->
        <template v-if="mode === 'branch-only'">
          <ScoreCard :score="branchScores.find(s => (s.category || s.scoreCategory) === 'overall')?.categoryScore || branchData.overallScore"
                     :rank="branchData.rankNum" label="综合得分" />
          <BranchScores :scores="branchScores" />
          <div class="info-section">
            <p>网点名称：{{ branchData.secondaryBranch }}</p>
            <p>地址：{{ branchData.address }}</p>
            <p>总面积：{{ branchData.totalArea }}m²</p>
            <p>总人数：{{ branchData.totalStaff }}人</p>
            <el-button type="text" @click="$emit('view-detail', 'branch')">查看详情 →</el-button>
          </div>
        </template>

        <!-- 对比模式 -->
        <template v-if="mode === 'split'">
          <div class="split-layout">
            <div class="split-left">
              <h4>📊 网格资源</h4>
              <ScoreCard :score="gridData.siteScore" :rank="gridRank" label="选址得分" size="small" />
              <IndicatorSection title="资源概况" :items="gridIndicators" compact />
              <p class="meta">编码：{{ gridData.gridCode }}</p>
            </div>
            <div class="split-right">
              <h4>🏦 网点经营</h4>
              <ScoreCard :score="branchScores.find(s => (s.category || s.scoreCategory) === 'overall')?.categoryScore || branchData.overallScore"
                         :rank="branchData.rankNum" label="综合得分" size="small" />
              <BranchScores :scores="branchScores" compact />
              <p class="meta">{{ branchData.secondaryBranch }} · {{ branchData.totalStaff }}人</p>
            </div>
          </div>
          <el-button type="text" style="width:100%;text-align:center;" @click="$emit('view-detail', 'all')">
            📋 查看详细指标数据表
          </el-button>
        </template>
      </div>
    </div>
  </transition>
</template>

<script>
import ScoreCard from './ScoreCard'
import IndicatorSection from './IndicatorSection'
import BranchScores from './BranchScores'

export default {
  name: 'SidebarPanel',
  components: { ScoreCard, IndicatorSection, BranchScores },
  props: {
    visible: Boolean,
    mode: { type: String, default: 'grid-only' },
    width: { type: Number, default: 380 },
    gridData: { type: Object, default: () => ({}) },
    gridRank: { type: Number, default: null },
    gridIndicators: { type: Array, default: () => [] },
    branchData: { type: Object, default: () => ({}) },
    branchScores: { type: Array, default: () => [] },
    years: { type: Array, default: () => [] },
    year: { type: Number, default: null }
  },
  computed: {
    title() {
      return { 'grid-only': '网格信息', 'branch-only': '网点信息', 'split': '网格详情（含网点）' }[this.mode] || '详情'
    },
    showYearPicker() {
      return this.mode === 'branch-only' || this.mode === 'split'
    }
  }
}
</script>

<style scoped>
.sidebar-panel {
  position: absolute; left: 12px; top: 60px; bottom: 12px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  z-index: 1000; display: flex; flex-direction: column; overflow: hidden;
  transition: width 0.3s ease;
}
.sidebar-header { padding: 14px 16px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.sidebar-title { font-weight: 600; font-size: 15px; color: #333; }
.sidebar-body { flex: 1; overflow-y: auto; padding: 12px 16px; }
.year-picker { margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }
.year-picker .label { font-size: 13px; color: #888; }
.info-section { font-size: 13px; color: #666; margin-top: 16px; padding-top: 12px; border-top: 1px solid #f0f0f0; }
.info-section p { margin: 4px 0; }
.split-layout { display: flex; gap: 16px; }
.split-left, .split-right { flex: 1; min-width: 0; }
.split-left h4, .split-right h4 { font-size: 13px; margin: 0 0 8px; color: #555; }
.meta { font-size: 12px; color: #999; margin-top: 8px; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s ease, opacity 0.3s ease; }
.slide-enter, .slide-leave-to { transform: translateX(-100%); opacity: 0; }
</style>
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/components/SidebarPanel.vue
git commit -m "feat: add SidebarPanel with grid/branch/split modes"
```

---

## Task 9: 排名列表组件 RankingList

**Files:**
- Create: `ruoyi-ui/src/views/jwmap/map/components/RankingList.vue`

数据格式约定：父组件传入的 `items` 应为 `[{ id, name, score }]` 格式。

- [ ] **创建 RankingList.vue**

```vue
<template>
  <div class="ranking-panel" v-if="visible">
    <div class="ranking-header">
      <span class="ranking-title">{{ title }}</span>
      <el-button type="text" icon="el-icon-close" size="mini" @click="$emit('close')" />
    </div>
    <div class="ranking-body">
      <div v-for="(item, i) in items" :key="item.id"
           class="ranking-item" @click="$emit('item-click', item)">
        <span class="rank-num">#{{ i + 1 + (page - 1) * pageSize }}</span>
        <span class="rank-name">{{ item.name }}</span>
        <span class="rank-score">{{ item.score.toFixed(2) }}</span>
      </div>
      <el-button v-if="hasMore" type="text" class="load-more" @click="$emit('load-more')" :loading="loading">
        查看更多 →
      </el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'RankingList',
  props: {
    visible: Boolean,
    title: { type: String, default: '排名列表' },
    items: { type: Array, default: () => [] },
    page: { type: Number, default: 1 },
    pageSize: { type: Number, default: 20 },
    hasMore: { type: Boolean, default: false },
    loading: { type: Boolean, default: false }
  }
}
</script>

<style scoped>
.ranking-panel {
  position: absolute; right: 12px; bottom: 12px; width: 260px; max-height: 360px;
  background: #fff; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  z-index: 1000; display: flex; flex-direction: column; overflow: hidden;
}
.ranking-header { padding: 10px 12px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.ranking-title { font-weight: 600; font-size: 13px; color: #333; }
.ranking-body { flex: 1; overflow-y: auto; padding: 4px 0; }
.ranking-item { display: flex; align-items: center; padding: 6px 12px; cursor: pointer; font-size: 13px; transition: background 0.15s; }
.ranking-item:hover { background: #f5f5f5; }
.rank-num { width: 36px; color: #888; font-variant-numeric: tabular-nums; }
.rank-name { flex: 1; color: #333; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-score { width: 48px; text-align: right; color: #52c41a; font-weight: 600; }
.load-more { width: 100%; text-align: center; font-size: 12px; }
</style>
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/components/RankingList.vue
git commit -m "feat: add RankingList component with pagination"
```

---

## Task 10: 集成到 tianditu.vue

**Files:**
- Modify: `ruoyi-ui/src/views/jwmap/map/tianditu.vue`

### 替换 template

```html
<div class="jw-map-container">
  <div id="jwmap-tianditu" ref="mapEl"></div>

  <TopToolbar
    :cities="cityBoundaries"
    :heatmapActive="heatmapVisible"
    @select-city="onSelectCity"
    @toggle-heatmap="onToggleHeatmap"
    @filter-branch="onFilterBranch" />

  <SidebarPanel
    :visible="sidebar.visible"
    :mode="sidebar.mode"
    :width="sidebar.width"
    :gridData="sidebar.gridData"
    :gridRank="sidebar.gridRank"
    :gridIndicators="sidebar.gridIndicators"
    :branchData="sidebar.branchData"
    :branchScores="sidebar.branchScores"
    :years="availableYears"
    :year="selectedYear"
    @close="closeSidebar"
    @view-detail="showDetailDialog"
    @year-change="onYearChange" />

  <RankingList
    :visible="ranking.visible"
    :title="ranking.title"
    :items="ranking.items"
    :page="ranking.page"
    :hasMore="ranking.hasMore"
    :loading="ranking.loading"
    @item-click="onRankingItemClick"
    @load-more="loadMoreRanking"
    @close="ranking.visible = false" />

  <el-dialog title="详细指标数据表" :visible.sync="detailDialog.visible" width="70%" top="5vh">
    <el-table :data="detailDialog.data" border size="small" max-height="60vh" style="width:100%">
      <el-table-column prop="name" label="指标名称" min-width="160" />
      <el-table-column prop="value" label="数值" width="120" />
    </el-table>
  </el-dialog>
</div>
```

### 替换 script

```javascript
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { TiandituBd09Crs } from './utils/tiandituCrs.js'
import { BoundaryManager } from './utils/boundaryManager.js'
import { MeasureTool } from './utils/measureTool.js'
import TopToolbar from './components/TopToolbar'
import SidebarPanel from './components/SidebarPanel'
import RankingList from './components/RankingList'
import { HeatmapLayer } from './utils/heatmapLayer'
import { getGridIndicators, getBranchScoreDetail, getGridBranches,
         getGridRanking, getBranchRanking } from '@/api/jwmap/data'
import '@/views/jwmap/map/assets/branch-icon.css'

export default {
  name: 'JwMapTianditu',
  components: { TopToolbar, SidebarPanel, RankingList },
  data() {
    return {
      map: null, boundaryMgr: null, measureTool: null,
      heatmapLayer: null, heatmapVisible: false,
      cityBoundaries: [],
      branchLayer: null,
      availableYears: [2022, 2023, 2024],
      selectedYear: 2024,
      sidebar: { visible: false, mode: 'grid-only', width: 380,
                 gridData: {}, gridRank: null, gridIndicators: [],
                 branchData: {}, branchScores: [] },
      ranking: { visible: false, title: '', items: [], page: 1, hasMore: false, loading: false, type: 'grid' },
      currentCity: null, currentFilter: null,
      detailDialog: { visible: false, data: [] }
    }
  },
  mounted() { this.$nextTick(() => this.initMap()) },
  beforeDestroy() { /* 清理 */ },
  methods: {
    initMap() {
      this.map = L.map(this.$refs.mapEl, {
        crs: TiandituBd09Crs, center: [26.5807, 106.7238], zoom: 10,
        minZoom: 9, maxZoom: 17,
        zoomControl: true, attributionControl: false,
        zoomAnimation: true, fadeAnimation: true
      })
      L.tileLayer('/tiles_tianditu/vec/{z}/{x}/{y}.png', { minZoom: 9, maxZoom: 17, tileSize: 256 }).addTo(this.map)
      L.tileLayer('/tiles_tianditu/cva/{z}/{x}/{y}.png', { minZoom: 9, maxZoom: 17, tileSize: 256 }).addTo(this.map)

      this.heatmapLayer = new HeatmapLayer(this.map)
      this.branchLayer = L.layerGroup().addTo(this.map)
      this.map.on('grid-click', (e) => this.onGridClick(e.gridCode, e.data))

      this.boundaryMgr = new BoundaryManager(this.map)
      this.boundaryMgr.init()
        .then(() => { this.cityBoundaries = this.boundaryMgr.cities || [] })
        .catch(err => console.error('[jwmap-tianditu] 边界加载失败:', err))
      this.measureTool = new MeasureTool(this.map)
      this.measureTool.init()
    },

    // ==== 工具栏事件 ====
    async onSelectCity(adcode) {
      if (!adcode) return
      this.currentCity = adcode
      this.currentFilter = null
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false }
      await this.loadBranches()
      this.loadRanking()
    },
    async onToggleHeatmap() {
      if (!this.currentCity) return
      if (this.heatmapVisible) { this.heatmapLayer.hide(); this.heatmapVisible = false; return }
      await this.heatmapLayer.loadData(this.currentCity)
      this.heatmapLayer.show()
      this.heatmapVisible = true
    },
    onFilterBranch(primaryBranch) {
      this.currentFilter = primaryBranch
      this.branchLayer.eachLayer(layer => {
        if (!primaryBranch) { this.map.addLayer(layer); return }
        const show = layer.branchData && layer.branchData.primaryBranch === primaryBranch
        if (show) { this.map.addLayer(layer) } else { this.map.removeLayer(layer) }
      })
    },
    async onYearChange(year) {
      this.selectedYear = year
      if (this.sidebar.visible && this.sidebar.branchData.branchId) {
        await this.loadBranchScores(this.sidebar.branchData.branchId)
      }
      this.loadRanking()
    },

    // ==== 网点 ====
    async loadBranches() {
      const { getBranchList } = await import('@/api/jwmap/data')
      const res = await getBranchList(this.currentCity)
      this.branchLayer.clearLayers()
      const branches = res.data || []
      const icon = L.divIcon({ className: 'branch-icon', html: '工', iconSize: [24, 24], iconAnchor: [12, 12] })
      for (const b of branches) {
        if (b.longitude == null || b.latitude == null) continue
        const marker = L.marker([b.latitude, b.longitude], { icon })
        marker.branchData = b
        marker.on('click', () => this.onBranchClick(b))
        this.branchLayer.addLayer(marker)
      }
    },
    async onBranchClick(branch) {
      this.sidebar.mode = 'branch-only'
      this.sidebar.width = 380
      this.sidebar.branchData = branch
      await this.loadBranchScores(branch.branchId)
      this.sidebar.visible = true
    },
    async loadBranchScores(branchId) {
      const res = await getBranchScoreDetail(branchId, this.selectedYear)
      this.sidebar.branchScores = res.data || []
    },

    // ==== 网格点击 ====
    async onGridClick(gridCode, data) {
      this.sidebar.gridData = data
      this.sidebar.gridRank = null  // 由父组件计算后传入
      // 计算排名：从 heatmapLayer._data 中找到 index
      if (this.heatmapLayer && this.heatmapLayer._data) {
        const idx = this.heatmapLayer._data.findIndex(d => d.gridCode === gridCode)
        if (idx >= 0) this.sidebar.gridRank = idx + 1
      }
      const indRes = await getGridIndicators(gridCode)
      this.sidebar.gridIndicators = (indRes.data || []).map(d => ({
        code: d.indicatorCode, name: d.indicatorCode, value: d.indicatorValue
      }))
      const brRes = await getGridBranches(gridCode)
      const branches = brRes.data || []
      if (branches.length > 0) {
        this.sidebar.mode = 'split'; this.sidebar.width = 600
        this.sidebar.branchData = branches[0]
        await this.loadBranchScores(branches[0].branchId)
      } else {
        this.sidebar.mode = 'grid-only'; this.sidebar.width = 380
      }
      this.sidebar.visible = true
    },

    // ==== 排名 ====
    loadRanking() {
      if (!this.currentCity) return
      this.ranking.type = 'grid'
      this.ranking.title = '🏆 网格选址排名'
      this.ranking.page = 1
      this.ranking.visible = true
      this.fetchRanking()
    },
    async fetchRanking() {
      if (!this.currentCity) return
      this.ranking.loading = true
      try {
        const fn = this.ranking.type === 'grid' ? getGridRanking : getBranchRanking
        const param = this.ranking.type === 'grid'
          ? this.currentCity
          : [this.currentCity, this.selectedYear]
        const res = await fn(...(Array.isArray(param) ? param : [param]))
        const rows = res.rows || res.data || []
        this.ranking.items = rows.map((r, i) => ({
          id: r.gridCode || r.branchId,
          name: r.gridCode || ('网点#' + r.branchId),
          score: r.siteScore || r.categoryScore || 0
        }))
        this.ranking.hasMore = rows.length >= (this.ranking.pageSize || 20)
      } catch (e) { this.ranking.items = [] }
      this.ranking.loading = false
    },
    loadMoreRanking() {
      this.ranking.page++
      this.fetchRanking()
    },
    onRankingItemClick(item) {
      // flyTo + 侧边栏
      if (item.id && this.heatmapLayer && this.heatmapLayer._data) {
        const found = this.heatmapLayer._data.find(d => d.gridCode === item.id)
        if (found) {
          this.map.flyTo([found.latitude, found.longitude], 14)
          this.onGridClick(found.gridCode, found)
        }
      }
    },

    // ==== 侧边栏 ====
    closeSidebar() { this.sidebar.visible = false },
    showDetailDialog(type) {
      let data = []
      if (type === 'grid' || type === 'all') {
        data = data.concat(this.sidebar.gridIndicators.map(i => ({ name: i.name, value: i.value })))
      }
      if (type === 'branch' || type === 'all') {
        data = data.concat(this.sidebar.branchScores.map(s => ({
          name: s.scoreCategory || s.category, value: s.categoryScore || s.score
        })))
      }
      this.detailDialog.data = data
      this.detailDialog.visible = true
    }
  }
}
```

- [ ] **提交**

```bash
git add ruoyi-ui/src/views/jwmap/map/tianditu.vue
git commit -m "feat: integrate all map visualization components into tianditu page"
```

---

## Verification

1. `mvn compile` 通过（后端编译）
2. 启动后端 `mvn spring-boot:run`，启动前端 `npm run dev`
3. 打开 http://localhost/jwmap/tianditu
4. 页面加载 → 显示天地图 + 贵州省边界
5. 选择地市 → 网点图标出现，排名列表更新
6. 点击热力图 → Canvas 热力层渲染
7. 点击网格 → 侧边栏滑出，显示得分和指标
8. 点击网点图标 → 侧边栏切换为网点信息 + 进度条
9. 网格内有网点 → 侧边栏 600px 对比模式
10. 切换年份 → 网点得分更新
11. 选择支行 → 地图过滤网点
