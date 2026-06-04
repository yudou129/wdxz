<template>
  <div class="indicator-mgmt">
    <!-- 使用说明 -->
    <el-collapse v-model="showGuide">
      <el-collapse-item name="guide">
        <template #title>
          <i class="el-icon-info" /> 如何使用指标管理
        </template>
        <div class="guide-content">
          <p><b>多级树结构：</b>通过"上级指标"字段形成任意深度的树。根节点（无上级）代表一个评分分类。</p>
          <p><b>只有叶子节点参与TOPSIS得分计算。</b>叶子节点 = 没有子指标的节点。</p>
          <p><b>有效权重 =</b> 本级权重 × 父级权重 × 祖父级权重 × ... × 根节点权重。</p>
          <p>5种类型：<el-tag size="mini" type="success">网点衍生(branch)</el-tag> 公式计算，<el-tag size="mini" type="info">网点原始(branch_raw)</el-tag> 正常录入，<el-tag size="mini">网格三聚焦(grid)</el-tag> 预设指标，<el-tag size="mini" type="warning">网点自动(branch_auto)</el-tag> 导入自动创建，<el-tag size="mini" type="danger">网格自动(grid_auto)</el-tag> 导入自动创建。</p>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 类型切换 -->
    <el-tabs v-model="activeType" @tab-click="onTabChange" class="type-tabs">
      <el-tab-pane label="网点指标" name="branch" />
      <el-tab-pane label="网格三聚焦指标" name="grid" />
      <el-tab-pane label="自动导入指标" name="auto_import" />
    </el-tabs>

    <div class="layout">
      <!-- 左侧树 -->
      <div class="left-tree">
        <div class="tree-toolbar">
          <el-button type="primary" size="mini" icon="el-icon-plus" @click="openAdd(null)">新增根指标</el-button>
          <el-button size="mini" icon="el-icon-delete" :disabled="checkedCodes.length === 0" @click="handleBatchDelete">批量删除({{ checkedCodes.length }})</el-button>
        </div>
        <el-tree
          ref="tree"
          :data="treeData"
          :props="{ children: 'children', label: 'indicatorName' }"
          node-key="indicatorCode"
          highlight-current
          show-checkbox
          @node-click="onNodeClick"
          @check="onCheck"
        >
          <span slot-scope="{ node, data }" class="tree-node">
            <span>{{ data.indicatorName }}</span>
            <span v-if="data.calculationWeight != null" class="node-weight">({{ data.calculationWeight }})</span>
            <span v-if="data.isLeaf" class="leaf-dot" title="叶子节点(参与TOPSIS)">🌿</span>
            <span v-if="data.isDerived === '1'" class="formula-dot" title="衍生计算指标">⚡</span>
          </span>
        </el-tree>
      </div>

      <!-- 右侧详情 -->
      <div class="right-detail" v-if="selected">
        <div class="detail-header">
          <span class="detail-title">指标详情</span>
          <el-button type="text" icon="el-icon-edit" @click="openEdit">编辑</el-button>
          <el-button type="text" icon="el-icon-delete" class="danger-text" @click="handleDelete">删除</el-button>
        </div>
        <el-form label-width="110px" size="small">
          <el-form-item label="指标名称">{{ selected.indicatorName }}</el-form-item>
          <el-form-item label="指标编码">{{ selected.indicatorCode }}</el-form-item>
          <el-form-item label="指标类型">{{ selected.indicatorType }}</el-form-item>
          <el-form-item label="上级指标">{{ selected.parentCode || '(根节点)' }}</el-form-item>
          <el-form-item label="是否衍生计算">{{ selected.isDerived === '1' ? '是' : '否' }}</el-form-item>
          <el-form-item v-if="selected.isDerived === '1'" label="计算模式">{{ selected.computationPattern }}</el-form-item>
          <el-form-item v-if="selected.isDerived === '1'" label="参与计算的指标">{{ selected.inputCodes }}</el-form-item>
          <el-form-item label="计算权重">{{ selected.calculationWeight }}</el-form-item>
        </el-form>

        <!-- 子指标列表 -->
        <div class="children-section">
          <div class="section-title">子指标 ({{ children.length }})</div>
          <el-table :data="children" size="mini" border>
            <el-table-column prop="indicatorCode" label="编码" show-overflow-tooltip />
            <el-table-column prop="indicatorName" label="名称" />
            <el-table-column prop="calculationWeight" label="权重" width="70" />
            <el-table-column prop="isDerived" label="衍生" width="55">
              <template slot-scope="{row}">{{ row.isDerived === '1' ? '是' : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template slot-scope="{row}">
                <el-button type="text" size="mini" @click="openEditChild(row)">编辑</el-button>
                <el-button type="text" size="mini" class="danger-text" @click="handleDeleteChild(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" size="mini" icon="el-icon-plus" style="margin-top:8px" @click="openAdd(selected)">新增子指标</el-button>
        </div>
      </div>

      <!-- 未选中提示 -->
      <div class="right-empty" v-else>
        <p>请在左侧树中选择一个指标查看详情</p>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="600px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="dialogForm" label-width="120px" size="small">
        <!-- 基本信息 -->
        <el-form-item label="指标名称" prop="indicatorName">
          <el-input v-model="form.indicatorName" placeholder="如: 人均营收" @blur="onNameBlur" />
        </el-form-item>
        <el-form-item label="指标编码" prop="indicatorCode">
          <el-input v-model="form.indicatorCode" placeholder="自动生成，可手动修改" />
        </el-form-item>
        <el-form-item label="指标类型" prop="indicatorType">
          <el-select v-model="form.indicatorType" @change="onTypeChange">
            <el-option label="网点衍生指标 (branch)" value="branch" />
            <el-option label="网格三聚焦指标 (grid)" value="grid" />
            <el-option label="网点原始数据 (branch_raw)" value="branch_raw" />
            <el-option label="网点自动导入 (branch_auto)" value="branch_auto" />
            <el-option label="网格自动导入 (grid_auto)" value="grid_auto" />
          </el-select>
          <span class="form-tip">{{ typeTips[form.indicatorType] }}</span>
        </el-form-item>
        <el-form-item label="上级节点">
          <el-input :value="parentName" disabled />
        </el-form-item>

        <!-- 计算设置 (仅 branch 类型) -->
        <template v-if="form.indicatorType === 'branch'">
          <el-form-item label="衍生计算">
            <el-switch v-model="form.isDerivedBool" @change="v => form.isDerived = v ? '1' : '0'" />
          </el-form-item>
          <template v-if="form.isDerivedBool">
            <el-form-item label="计算模式" prop="computationPattern">
              <el-select v-model="form.computationPattern" @change="onPatternChange">
                <el-option v-for="p in patterns" :key="p.value" :label="p.label" :value="p.value" />
              </el-select>
              <div class="form-tip">{{ patternDesc[form.computationPattern] }}</div>
            </el-form-item>
            <el-form-item label="参与计算的指标" prop="inputCodesDisplay">
              <!-- 单个选择 (per_capita, per_area) -->
              <template v-if="isSingleInput">
                <el-select v-model="form.inputSelections[0]" filterable placeholder="选择原始指标">
                  <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                </el-select>
              </template>
              <!-- 多选 (sum_per_capita, sum_per_area) -->
              <template v-else-if="isMultiInput">
                <div v-for="(sel, idx) in form.inputSelections" :key="idx" class="input-row">
                  <el-select v-model="form.inputSelections[idx]" filterable placeholder="选择原始指标">
                    <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                  </el-select>
                  <el-button type="text" icon="el-icon-close" @click="removeInput(idx)" v-if="form.inputSelections.length > 1" />
                </div>
                <el-button type="text" size="mini" icon="el-icon-plus" @click="addInput">添加指标</el-button>
              </template>
              <!-- per_customer: 多指标加减求和 ÷ 多指标加减求和 -->
              <template v-else-if="form.computationPattern === 'per_customer'">
                <div class="section-label">被除数（分子）:</div>
                <div v-for="(sel, idx) in form.inputSelectionsNum" :key="'num-'+idx" class="input-row">
                  <el-button
                    :type="form.inputSignsNum[idx] === '+' ? 'primary' : 'danger'"
                    size="mini" @click="toggleSign('num', idx)" style="width:36px;padding:5px 0">
                    {{ form.inputSignsNum[idx] }}
                  </el-button>
                  <el-select v-model="form.inputSelectionsNum[idx]" filterable placeholder="选择指标">
                    <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                  </el-select>
                  <el-button type="text" icon="el-icon-close" @click="removeNumInput(idx)" v-if="form.inputSelectionsNum.length > 1" />
                </div>
                <el-button type="text" size="mini" icon="el-icon-plus" @click="addNumInput">添加分子指标</el-button>

                <div class="section-label" style="margin-top:12px;">除数（分母）:</div>
                <div v-for="(sel, idx) in form.inputSelectionsDen" :key="'den-'+idx" class="input-row">
                  <el-button
                    :type="form.inputSignsDen[idx] === '+' ? 'primary' : 'danger'"
                    size="mini" @click="toggleSign('den', idx)" style="width:36px;padding:5px 0">
                    {{ form.inputSignsDen[idx] }}
                  </el-button>
                  <el-select v-model="form.inputSelectionsDen[idx]" filterable placeholder="选择指标">
                    <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                  </el-select>
                  <el-button type="text" icon="el-icon-close" @click="removeDenInput(idx)" v-if="form.inputSelectionsDen.length > 1" />
                </div>
                <el-button type="text" size="mini" icon="el-icon-plus" @click="addDenInput">添加分母指标</el-button>
              </template>
              <!-- growth_rate: 两个选择 -->
              <template v-else-if="form.computationPattern === 'growth_rate'">
                <div class="input-row">
                  <span class="input-label">余额指标:</span>
                  <el-select v-model="form.inputSelections[0]" filterable placeholder="如: 全量个人金融资产">
                    <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                  </el-select>
                </div>
                <div class="input-row">
                  <span class="input-label">增量指标:</span>
                  <el-select v-model="form.inputSelections[1]" filterable placeholder="如: 全量个人金融资产日均增量">
                    <el-option v-for="r in rawIndicators" :key="r.indicatorCode" :label="r.indicatorName" :value="r.indicatorCode" />
                  </el-select>
                </div>
              </template>
              <div class="formula-preview" v-if="formulaPreview">
                📐 {{ formulaPreview }}
              </div>
            </el-form-item>
          </template>
        </template>

        <!-- 权重设置 -->
        <el-form-item label="计算权重">
          <el-input-number v-model="form.calculationWeight" :min="0" :max="1" :precision="4" :step="0.1" />
          <span class="form-tip" v-if="weightPath">实际有效权重: {{ effectiveWeight }} ({{ weightPath }})</span>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { getIndicatorTree, getIndicatorList, generateIndicatorCode, saveIndicator, updateIndicator, deleteIndicator, batchDeleteIndicators } from '@/api/jwmap/data'

export default {
  name: 'IndicatorConfig',
  data() {
    return {
      showGuide: ['guide'],
      activeType: 'branch',
      treeData: [],
      selected: null,
      children: [],
      dialogVisible: false,
      isEdit: false,
      editingId: null,
      parentNode: null,
      rawIndicators: [],
      checkedCodes: [],
      form: {
        indicatorName: '',
        indicatorCode: '',
        indicatorType: 'branch',
        parentCode: null,
        isDerived: '0',
        isDerivedBool: false,
        computationPattern: '',
        inputSelections: [''],
        inputSelectionsNum: [''],
        inputSignsNum: ['+'],
        inputSelectionsDen: [''],
        inputSignsDen: ['+'],
        calculationWeight: null,
        sortOrder: 0
      },
      rules: {
        indicatorName: [{ required: true, message: '请输入指标名称' }],
        indicatorCode: [{ required: true, message: '请输入或生成指标编码' }],
        indicatorType: [{ required: true }],
        computationPattern: [{ required: true, message: '请选择计算模式', trigger: 'change' }]
      },
      typeTips: {
        branch: '公式计算得出，参与TOPSIS评分',
        grid: '网格三聚焦预设指标，参与TOPSIS评分',
        branch_raw: '正常录入的网点原始数据，参与TOPSIS评分',
        branch_auto: '导入Excel时自动创建的网点指标',
        grid_auto: '导入Excel时自动创建的网格指标'
      },
      patterns: [
        { value: 'per_capita', label: 'per_capita — 指标值 ÷ 员工总数' },
        { value: 'per_area', label: 'per_area — 指标值 ÷ 网点面积' },
        { value: 'sum_per_capita', label: 'sum_per_capita — 多指标求和 ÷ 员工总数' },
        { value: 'sum_per_area', label: 'sum_per_area — 多指标求和 ÷ 网点面积' },
        { value: 'per_customer', label: 'per_customer — 多指标加减求和 ÷ 多指标加减求和' },
        { value: 'growth_rate', label: 'growth_rate — 本年增量 ÷ 上年基数' }
      ],
      patternDesc: {
        per_capita: '把选中的指标值 ÷ 员工总数',
        per_area: '把选中的指标值 ÷ 网点面积',
        sum_per_capita: '把选中的几个指标加起来 ÷ 员工总数',
        sum_per_area: '把选中的几个指标加起来 ÷ 网点面积',
        per_customer: '多指标（可正可负）求和 ÷ 多指标（可正可负）求和，+/- 按钮切换符号',
        growth_rate: '本年增长量 ÷ 去年基数 = 同比增长率'
      }
    }
  },
  computed: {
    parentName() {
      return this.parentNode ? this.parentNode.indicatorName : '(根节点)'
    },
    isSingleInput() {
      return ['per_capita', 'per_area'].includes(this.form.computationPattern)
    },
    isMultiInput() {
      return ['sum_per_capita', 'sum_per_area'].includes(this.form.computationPattern)
    },
    dialogTitle() {
      return this.isEdit ? '编辑指标' : '新增指标'
    },
    formulaPreview() {
      const pattern = this.form.computationPattern
      if (!pattern) return ''
      const sel = this.form.inputSelections.filter(s => s)
      const names = sel.map(c => {
        const found = this.rawIndicators.find(r => r.indicatorCode === c)
        return found ? found.indicatorName : c
      })
      switch (pattern) {
        case 'per_capita': return names[0] ? `${names[0]} ÷ 总员工数` : ''
        case 'per_area': return names[0] ? `${names[0]} ÷ 总面积` : ''
        case 'sum_per_capita': return names.length ? `(${names.join(' + ')}) ÷ 总员工数` : ''
        case 'sum_per_area': return names.length ? `(${names.join(' + ')}) ÷ 总面积` : ''
        case 'per_customer': {
          const numSel = this.form.inputSelectionsNum || []
          const denSel = this.form.inputSelectionsDen || []
          const toName = (code, sign) => {
            if (!code) return ''
            const found = this.rawIndicators.find(r => r.indicatorCode === code)
            return (sign === '-' ? '-' : '+') + (found ? found.indicatorName : code)
          }
          const numStr = numSel.map((c, i) => toName(c, this.form.inputSignsNum[i])).filter(Boolean).join(' ').replace(/^\+/, '')
          const denStr = denSel.map((c, i) => toName(c, this.form.inputSignsDen[i])).filter(Boolean).join(' ').replace(/^\+/, '')
          return numStr && denStr ? `(${numStr}) ÷ (${denStr})` : ''
        }
        case 'growth_rate': return names.length === 2 ? `(${names[1]})本年增量 ÷ (${names[0]})上年基数` : ''
        default: return ''
      }
    },
    weightPath() {
      if (!this.parentNode || this.form.calculationWeight == null) return ''
      const path = []
      let node = this.parentNode
      while (node) {
        path.unshift(`${node.indicatorName}(${node.calculationWeight || '-'})`)
        node = node._parent
      }
      path.push(`${this.form.indicatorName || '当前'}(${this.form.calculationWeight})`)
      return path.join(' → ')
    },
    effectiveWeight() {
      if (this.form.calculationWeight == null) return '-'
      let w = this.form.calculationWeight
      // 沿 parentNode 链向上累乘
      let p = this.parentNode
      while (p) {
        if (p.calculationWeight != null) w *= p.calculationWeight
        p = p._parent
      }
      return w.toFixed(6)
    }
  },
  created() {
    this.loadTree()
    this.loadRawIndicators()
  },
  methods: {
    onTabChange() {
      this.selected = null
      this.children = []
      this.checkedCodes = []
      this.loadTree()
    },
    async loadTree() {
      try {
        let typeParam = this.activeType
        if (typeParam === 'branch') {
          typeParam = 'branch,branch_raw'
        } else if (typeParam === 'auto_import') {
          typeParam = 'branch_auto,grid_auto'
        }
        const res = await getIndicatorTree(typeParam)
        this.treeData = this.processTreeData(res.data || [])
      } catch (e) {
        this.$message.error('加载指标树失败')
      }
    },
    processTreeData(nodes, parent) {
      return nodes.map(n => {
        n._parent = parent
        if (n.children && n.children.length) {
          n.children = this.processTreeData(n.children, n)
        }
        return n
      })
    },
    async loadRawIndicators() {
      try {
        const res = await getIndicatorList()
        const rawList = (res.data || [])
          .filter(i => i.indicatorType === 'branch_raw' || i.indicatorType === 'branch' || i.indicatorType === 'branch_auto' || i.indicatorType === 'grid_auto')

        // 从树数据构建上级路径映射，同名指标可通过其上级归属区分
        const pathMap = this.buildIndicatorPathMap()

        this.rawIndicators = rawList.map(i => ({
          indicatorCode: i.indicatorCode,
          indicatorName: pathMap[i.indicatorCode]
            ? `${pathMap[i.indicatorCode]} > ${i.indicatorName} (${i.indicatorCode})`
            : `${i.indicatorName} (${i.indicatorCode})`,
          _path: pathMap[i.indicatorCode] || ''
        })).sort((a, b) => {
          if (a._path !== b._path) return a._path.localeCompare(b._path)
          return a.indicatorName.localeCompare(b.indicatorName)
        })
      } catch (e) {
        console.warn('加载可引用指标列表失败', e)
      }
    },
    /** 遍历树数据，为每个指标构建"上级 > 上上级 > ..."的路径字符串 */
    buildIndicatorPathMap() {
      const map = {}
      const walk = (nodes, ancestors) => {
        for (const n of nodes) {
          map[n.indicatorCode] = ancestors.join(' > ')
          if (n.children && n.children.length) {
            walk(n.children, [...ancestors, n.indicatorName])
          }
        }
      }
      walk(this.treeData, [])
      return map
    },
    onNodeClick(data) {
      this.selected = data
      this.children = (data.children || []).filter(c => c.indicatorCode !== data.indicatorCode)
    },
    onCheck() {
      this.checkedCodes = this.$refs.tree.getCheckedKeys()
    },
    openAdd(parent) {
      this.isEdit = false
      this.editingId = null
      this.parentNode = parent || null
      this.resetForm()
      // auto_import 标签页下新增时默认选中 branch_raw（用户可手动切换）
      this.form.indicatorType = this.activeType === 'auto_import' ? 'branch_raw' : this.activeType
      this.form.parentCode = parent ? parent.indicatorCode : null
      this.loadRawIndicators()
      this.dialogVisible = true
    },
    openEdit() {
      this.isEdit = true
      this.editingId = this.selected.indicatorId
      this.parentNode = this.selected._parent
      this.resetForm()
      this.form.indicatorName = this.selected.indicatorName
      this.form.indicatorCode = this.selected.indicatorCode
      this.form.indicatorType = this.selected.indicatorType
      this.form.parentCode = this.selected.parentCode
      this.form.calculationWeight = this.selected.calculationWeight
      this.form.sortOrder = this.selected.sortOrder || 0
      this.form.isDerived = this.selected.isDerived || '0'
      this.form.isDerivedBool = this.form.isDerived === '1'
      this.form.computationPattern = this.selected.computationPattern || ''
      this.parseInputCodesToSelections()
      this.dialogVisible = true
    },
    openEditChild(row) {
      const node = this.findNodeByCode(this.treeData, row.indicatorCode)
      if (node) {
        this.selected = node
        this.openEdit()
      }
    },
    findNodeByCode(nodes, code) {
      for (const n of nodes) {
        if (n.indicatorCode === code) return n
        if (n.children) {
          const found = this.findNodeByCode(n.children, code)
          if (found) return found
        }
      }
      return null
    },
    parseInputCodesToSelections() {
      if (!this.selected || !this.selected.inputCodes) {
        this.form.inputSelections = ['']
      } else {
        const pattern = this.selected.computationPattern
        const codes = this.selected.inputCodes
        if (['per_capita', 'per_area'].includes(pattern)) {
          this.form.inputSelections = [codes]
        } else if (['sum_per_capita', 'sum_per_area'].includes(pattern)) {
          this.form.inputSelections = codes.split(',')
        } else if (pattern === 'per_customer') {
          // Format: "+code1,-code2|+code3,+code4"
          const parts = codes.split('|')
          this.form.inputSelectionsNum = ['']
          this.form.inputSignsNum = ['+']
          this.form.inputSelectionsDen = ['']
          this.form.inputSignsDen = ['+']
          const parseSigned = (part) => {
            if (!part) return { codes: [''], signs: ['+'] }
            const tokens = part.split(',').filter(t => t.trim())
            if (tokens.length === 0) return { codes: [''], signs: ['+'] }
            const c = [], s = []
            for (const t of tokens) {
              if (t.startsWith('-')) { s.push('-'); c.push(t.substring(1)) }
              else { s.push('+'); c.push(t.startsWith('+') ? t.substring(1) : t) }
            }
            return { codes: c, signs: s }
          }
          const num = parseSigned(parts[0])
          this.form.inputSelectionsNum = num.codes
          this.form.inputSignsNum = num.signs
          if (parts.length > 1) {
            const den = parseSigned(parts[1])
            this.form.inputSelectionsDen = den.codes
            this.form.inputSignsDen = den.signs
          }
        } else if (pattern === 'growth_rate') {
          this.form.inputSelections = codes.split('|')
        } else {
          this.form.inputSelections = ['']
        }
      }
    },
    async onNameBlur() {
      if (this.isEdit) return
      if (!this.form.indicatorName) return
      try {
        const res = await generateIndicatorCode(this.form.indicatorName, this.form.indicatorType)
        if (res.data && res.data.code) {
          this.form.indicatorCode = res.data.code
        }
      } catch (e) { /* ignore */ }
    },
    onTypeChange() {
      this.form.isDerivedBool = this.form.indicatorType === 'branch'
      this.form.isDerived = this.form.indicatorType === 'branch' ? '1' : '0'
    },
    onPatternChange() {
      this.form.inputSelections = ['']
      this.form.inputSelectionsNum = ['']
      this.form.inputSignsNum = ['+']
      this.form.inputSelectionsDen = ['']
      this.form.inputSignsDen = ['+']
    },
    addInput() { this.form.inputSelections.push('') },
    removeInput(idx) { this.form.inputSelections.splice(idx, 1) },
    addNumInput() { this.form.inputSelectionsNum.push(''); this.form.inputSignsNum.push('+') },
    removeNumInput(idx) { this.form.inputSelectionsNum.splice(idx, 1); this.form.inputSignsNum.splice(idx, 1) },
    addDenInput() { this.form.inputSelectionsDen.push(''); this.form.inputSignsDen.push('+') },
    removeDenInput(idx) { this.form.inputSelectionsDen.splice(idx, 1); this.form.inputSignsDen.splice(idx, 1) },
    toggleSign(part, idx) {
      const key = part === 'num' ? 'inputSignsNum' : 'inputSignsDen'
      this.$set(this.form[key], idx, this.form[key][idx] === '+' ? '-' : '+')
    },
    resetForm() {
      this.form = {
        indicatorName: '', indicatorCode: '', indicatorType: 'branch',
        parentCode: null, isDerived: '0', isDerivedBool: false,
        computationPattern: '', inputSelections: [''],
        inputSelectionsNum: [''],
        inputSignsNum: ['+'],
        inputSelectionsDen: [''],
        inputSignsDen: ['+'],
        calculationWeight: null, sortOrder: 0
      }
    },
    buildInputCodes() {
      const pattern = this.form.computationPattern
      switch (pattern) {
        case 'per_capita':
        case 'per_area': {
          const sel = this.form.inputSelections.filter(s => s)
          return sel.length === 0 ? null : sel[0]
        }
        case 'sum_per_capita':
        case 'sum_per_area': {
          const sel = this.form.inputSelections.filter(s => s)
          return sel.length === 0 ? null : sel.join(',')
        }
        case 'per_customer': {
          const numCodes = this.form.inputSelectionsNum.filter(s => s)
          const denCodes = this.form.inputSelectionsDen.filter(s => s)
          if (numCodes.length === 0 || denCodes.length === 0) return null
          const numPart = numCodes.map((c, i) => (this.form.inputSignsNum[i] || '+') + c).join(',')
          const denPart = denCodes.map((c, i) => (this.form.inputSignsDen[i] || '+') + c).join(',')
          return numPart + '|' + denPart
        }
        case 'growth_rate': {
          const sel = this.form.inputSelections.filter(s => s)
          return sel.length < 2 ? null : sel[0] + '|' + sel[1]
        }
        default:
          return null
      }
    },
    async handleSave() {
      try {
        await this.$refs.dialogForm.validate()
      } catch { return }

      const data = {
        indicatorName: this.form.indicatorName,
        indicatorCode: this.form.indicatorCode,
        indicatorType: this.form.indicatorType,
        parentCode: this.form.parentCode || null,
        isDerived: this.form.isDerivedBool ? '1' : '0',
        computationPattern: this.form.isDerivedBool ? this.form.computationPattern : null,
        inputCodes: this.form.isDerivedBool ? this.buildInputCodes() : null,
        calculationWeight: this.form.calculationWeight,
        sortOrder: this.form.sortOrder
      }

      try {
        if (this.isEdit) {
          await updateIndicator(this.editingId, data)
          this.$message.success('更新成功')
        } else {
          await saveIndicator(data)
          this.$message.success('新增成功')
        }
        this.dialogVisible = false
        this.selected = null
        this.children = []
        this.loadTree()
      } catch (e) {
        this.$message.error('操作失败: ' + (e.message || '未知错误'))
      }
    },
    async handleDelete() {
      try {
        await this.$confirm('删除该指标将同时删除所有子指标及相关数据（人口热力、网格数据、网点指标等），确定继续?', '确认删除', { type: 'warning' })
      } catch { return }
      try {
        await deleteIndicator(this.selected.indicatorId)
        this.$message.success('删除成功')
        this.selected = null
        this.children = []
        this.loadTree()
      } catch (e) {
        this.$message.error('删除失败: ' + (e.message || '未知错误'))
      }
    },
    async handleDeleteChild(row) {
      try {
        await this.$confirm('确定删除该指标?', '确认删除', { type: 'warning' })
      } catch { return }
      try {
        await deleteIndicator(row.indicatorId)
        this.$message.success('删除成功')
        this.loadTree()
      } catch (e) {
        this.$message.error('删除失败: ' + (e.message || '未知错误'))
      }
    },
    async handleBatchDelete() {
      if (this.checkedCodes.length === 0) return
      try {
        await this.$confirm(`确定删除选中的 ${this.checkedCodes.length} 个指标及其所有子指标和相关数据？此操作不可恢复。`, '批量删除确认', { type: 'warning', confirmButtonText: '确认删除' })
      } catch { return }
      try {
        await batchDeleteIndicators(this.checkedCodes)
        this.$message.success(`成功删除 ${this.checkedCodes.length} 个指标`)
        this.checkedCodes = []
        this.selected = null
        this.children = []
        this.loadTree()
      } catch (e) {
        this.$message.error('批量删除失败: ' + (e.message || '未知错误'))
      }
    }
  }
}
</script>

<style scoped>
.indicator-mgmt { padding: 16px; }
.type-tabs { margin-bottom: 12px; }
.guide-content { font-size: 13px; color: #555; line-height: 1.8; }
.guide-content p { margin: 4px 0; }
.layout { display: flex; gap: 16px; min-height: 500px; }
.left-tree { width: 280px; flex-shrink: 0; border: 1px solid #e8e8e8; border-radius: 6px; padding: 12px; overflow-y: auto; max-height: 600px; }
.right-detail { flex: 1; border: 1px solid #e8e8e8; border-radius: 6px; padding: 16px; }
.right-empty { flex: 1; display: flex; align-items: center; justify-content: center; color: #999; }
.tree-node { display: flex; align-items: center; flex: 1; }
.node-weight { font-size: 11px; color: #999; margin-left: 4px; }
.leaf-dot { margin-left: 4px; font-size: 12px; }
.formula-dot { margin-left: 2px; font-size: 11px; }
.detail-header { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; border-bottom: 1px solid #eee; padding-bottom: 8px; }
.detail-title { font-weight: 600; font-size: 15px; flex: 1; }
.children-section { margin-top: 20px; }
.section-title { font-weight: 600; font-size: 13px; color: #555; margin-bottom: 8px; }
.form-tip { font-size: 12px; color: #999; margin-left: 8px; }
.formula-preview { margin-top: 8px; padding: 8px 12px; background: #f0f6ff; border-radius: 4px; font-size: 13px; color: #4f6ef6; }
.input-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.input-label { width: 80px; font-size: 12px; color: #666; text-align: right; flex-shrink: 0; }
.section-label { font-size: 13px; font-weight: 600; color: #444; margin-bottom: 6px; }
.danger-text { color: #f56c6c; }
.tree-toolbar { display: flex; gap: 6px; margin-bottom: 10px; flex-wrap: wrap; }
.tree-toolbar .el-button { flex-shrink: 0; }
</style>
