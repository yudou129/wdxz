#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
贵州省全量测试数据生成器
输出: jw_test_data_full.sql (约 12MB, 113,600+ 行)

用法: python sql/generate_test_data.py
"""

import math, random, re, os, sys
from datetime import datetime, date

random.seed(42)

# ── 路径 ──
BASE = os.path.dirname(os.path.abspath(__file__))
OUTPUT  = os.path.join(BASE, 'jw_test_data_full.sql')
CONFIG_SQL = os.path.join(BASE, 'jw_indicator_config.sql')

# ── 常量 ──
KM_PER_DEG   = 111.32
GRID_STEP    = 0.5 / KM_PER_DEG     # ~0.00449°
GRID_SIZE    = GRID_STEP * 2        # ~1km 网格步长
NOW   = datetime.now()
YEARS = [2022, 2023, 2024]

# Bcrypt hash for 'admin123'
BCRYPT = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2'

# ===================================================================
# 1. 城市配置
# ===================================================================

CITY_CONFIG = [
    {
        'abbr': 'GY', 'name': '贵阳市',   'adcode': 520100,
        'center': (106.7238, 26.5807),
        'bbox': (106.30, 26.35, 107.10, 26.75),
        'gx': 400, 'districts': ['南明区','云岩区','花溪区','乌当区','白云区','观山湖区','清镇市','开阳县','修文县','息烽县'],
        'primary_branches': ['贵阳分行','清镇市支行','开阳县支行','修文县支行','息烽县支行'],
        'branch_count': 25,
    },
    {
        'abbr': 'ZY', 'name': '遵义市',   'adcode': 520300,
        'center': (106.9370, 27.6950),
        'bbox': (106.40, 27.50, 107.30, 27.85),
        'gx': 250, 'districts': ['红花岗区','汇川区','播州区','新蒲新区','仁怀市','赤水市','正安县','桐梓县','湄潭县'],
        'primary_branches': ['遵义分行','仁怀市支行','赤水市支行','正安县支行','桐梓县支行'],
        'branch_count': 18,
    },
    {
        'abbr': 'LPS', 'name': '六盘水市', 'adcode': 520200,
        'center': (104.8300, 26.5950),
        'bbox': (104.50, 26.35, 105.10, 26.80),
        'gx': 150, 'districts': ['钟山区','水城区','六枝特区','盘州市'],
        'primary_branches': ['六盘水分行','盘州市支行','六枝特区支行'],
        'branch_count': 14,
    },
    {
        'abbr': 'AS', 'name': '安顺市',   'adcode': 520400,
        'center': (105.9470, 26.2550),
        'bbox': (105.65, 26.10, 106.20, 26.45),
        'gx': 100, 'districts': ['西秀区','平坝区','普定县','镇宁县','关岭县','紫云县'],
        'primary_branches': ['安顺分行','平坝区支行','普定县支行','镇宁县支行','关岭县支行'],
        'branch_count': 12,
    },
    {
        'abbr': 'BJ', 'name': '毕节市',   'adcode': 520500,
        'center': (105.2900, 27.3000),
        'bbox': (104.95, 27.00, 105.70, 27.55),
        'gx': 150, 'districts': ['七星关区','大方县','黔西市','金沙县','织金县','纳雍县','赫章县','威宁县'],
        'primary_branches': ['毕节分行','大方县支行','黔西市支行','金沙县支行','织金县支行'],
        'branch_count': 15,
    },
    {
        'abbr': 'TR', 'name': '铜仁市',   'adcode': 520600,
        'center': (109.1800, 27.7300),
        'bbox': (108.85, 27.50, 109.50, 28.00),
        'gx': 100, 'districts': ['碧江区','万山区','江口县','玉屏县','石阡县','思南县','印江县','德江县','沿河县'],
        'primary_branches': ['铜仁分行','江口县支行','玉屏县支行','思南县支行'],
        'branch_count': 12,
    },
    {
        'abbr': 'QXN', 'name': '黔西南州', 'adcode': 522300,
        'center': (104.9000, 25.0900),
        'bbox': (104.55, 24.80, 105.30, 25.40),
        'gx': 100, 'districts': ['兴义市','兴仁市','普安县','晴隆县','贞丰县','望谟县','册亨县','安龙县'],
        'primary_branches': ['黔西南分行','兴仁市支行','普安县支行','贞丰县支行','安龙县支行'],
        'branch_count': 12,
    },
    {
        'abbr': 'QDN', 'name': '黔东南州', 'adcode': 522600,
        'center': (107.9700, 26.5800),
        'bbox': (107.60, 26.25, 108.40, 26.90),
        'gx': 100, 'districts': ['凯里市','黄平县','施秉县','三穗县','镇远县','岑巩县','天柱县','锦屏县','剑河县','台江县','黎平县','榕江县','从江县','雷山县','麻江县','丹寨县'],
        'primary_branches': ['黔东南分行','黄平县支行','镇远县支行','黎平县支行','榕江县支行'],
        'branch_count': 12,
    },
    {
        'abbr': 'QN', 'name': '黔南州',   'adcode': 522700,
        'center': (107.5200, 26.2500),
        'bbox': (107.15, 25.90, 107.90, 26.55),
        'gx': 100, 'districts': ['都匀市','福泉市','荔波县','贵定县','瓮安县','独山县','平塘县','罗甸县','长顺县','龙里县','惠水县','三都县'],
        'primary_branches': ['黔南分行','福泉市支行','瓮安县支行','独山县支行','龙里县支行','惠水县支行'],
        'branch_count': 12,
    },
]

# 同业银行列表 (14家)
PEER_BANKS = [
    '中国建设银行', '中国农业银行', '中国银行', '交通银行',
    '招商银行', '中国邮政储蓄银行', '中信银行', '浦发银行',
    '民生银行', '兴业银行', '光大银行', '平安银行',
    '华夏银行', '贵阳银行',
]

# 真实 ICBC 支行/网点名称后缀
BRANCH_SUFFIXES = ['营业部', '支行', '分理处', '储蓄所']
BRANCH_TYPES = ['分行营业部', '城区支行', '县域支行', '乡镇支行', '支行营业部', '精品网点', '综合网点']
MANAGER_NAMES = ['张明','李莉','王强','陈芳','刘伟','赵刚','孙梅','周强','吴燕','郑勇','黄丽','谢华','唐敏','何丽','秦勇','宋洁','杨军','胡波','林峰','高远','罗琳','梁浩','苏婷','邓辉','潘涛']

# 支行名 → dept_id 映射（生成时可用的占位，运行时动态递增）
DEPT_NAMES = {}  # filled during generation

# 省级/市级/支行级 dept_id 空间
NEXT_DEPT_ID = 259  # 现有数据用到 258
NEXT_USER_ID = 100
NEXT_BRANCH_INFO_ID = 100  # auto_increment 起点

# ===================================================================
# 2. 解析指标配置
# ===================================================================

def parse_indicator_config():
    """解析 jw_indicator_config.sql，返回指标列表和树结构"""
    indicators = []
    with open(CONFIG_SQL, 'r', encoding='utf-8') as f:
        content = f.read()

    # 提取 VALUES 之后到最后一个分号之间的所有内容
    m = re.search(r"VALUES\s*(.*?);\s*$", content, re.DOTALL)
    if not m:
        print("WARNING: 无法解析 indicator_config SQL")
        return [], {}, set()

    values_text = m.group(1).strip()

    # 按 "),(  " 分割行（注意: SQL 各行以 ),\n( 或 ),\n 分隔）
    # 移除末尾逗号再按 )\n( 拆分
    values_text = values_text.strip().rstrip(';').rstrip(',')

    # 逐字符解析括号嵌套，提取每个顶级括号组
    # 方法: 找到每个以 ( 开头、) 结尾的顶级组
    i = 0
    groups = []
    while i < len(values_text):
        if values_text[i] == '(':
            depth = 0
            start = i
            while i < len(values_text):
                if values_text[i] == '(':
                    depth += 1
                elif values_text[i] == ')':
                    depth -= 1
                    if depth == 0:
                        groups.append(values_text[start:i+1])
                        break
                i += 1
        i += 1

    for g in groups:
        # 用简单方法拆分字段: 按逗号分割，但忽略引号内的逗号
        inner = g[1:-1]  # 去掉外层 ()
        fields = split_sql_values(inner)
        if len(fields) < 14:
            continue

        def clean(s):
            s = s.strip().strip("'").strip()
            return s if s and s != 'NULL' else ''

        try:
            indicator = {
                'id': int(clean(fields[0])),
                'code': clean(fields[1]),
                'name': clean(fields[2]),
                'type': clean(fields[8]),
                'parent': clean(fields[9]),
                'is_derived': clean(fields[10]),
                'weight': float(clean(fields[13])) if clean(fields[13]) else 0,
            }
            indicators.append(indicator)
        except (ValueError, IndexError):
            continue

    print(f"  解析到 {len(indicators)} 个指标")
    return indicators, build_children_map(indicators), calc_leaf_codes(indicators)

def split_sql_values(text):
    """按逗号分割 SQL 值，正确处理引号内的逗号和 NULL"""
    result = []
    current = []
    in_quote = False
    i = 0
    while i < len(text):
        ch = text[i]
        if ch == "'":
            in_quote = not in_quote
            current.append(ch)
        elif ch == ',' and not in_quote:
            result.append(''.join(current).strip())
            current = []
        else:
            current.append(ch)
        i += 1
    if current:
        result.append(''.join(current).strip())
    return result

def build_children_map(indicators):
    children = {}
    for ind in indicators:
        p = ind['parent']
        if p:
            children.setdefault(p, set()).add(ind['code'])
    return children

def calc_leaf_codes(indicators):
    children = build_children_map(indicators)
    all_codes = {ind['code'] for ind in indicators}
    return {ind['code'] for ind in indicators if ind['code'] not in children or not children[ind['code']]}

    # 构建父→子映射
    children_map = {}
    for ind in indicators:
        p = ind['parent']
        if p:
            children_map.setdefault(p, set()).add(ind['code'])

    # 确定叶子节点：没有子节点的
    all_codes = {ind['code'] for ind in indicators}
    leaf_codes = set()
    for ind in indicators:
        code = ind['code']
        if code not in children_map or not children_map[code]:
            leaf_codes.add(code)

    print(f"  解析到 {len(indicators)} 个指标, {len(leaf_codes)} 个叶子")
    print(f"  按类型: grid={sum(1 for i in indicators if i['type']=='grid')}, grid_raw={sum(1 for i in indicators if i['type']=='grid_raw')}, "
          f"grid_auto={sum(1 for i in indicators if i['type']=='grid_auto')}, "
          f"branch={sum(1 for i in indicators if i['type']=='branch')}, branch_raw={sum(1 for i in indicators if i['type']=='branch_raw')}")

    return indicators, children_map, leaf_codes

# ===================================================================
# 3. SQL 写入器
# ===================================================================

class SqlWriter:
    def __init__(self, path):
        self.path = path
        self.f = None
        self.buf = []
        self.total_rows = 0

    def __enter__(self):
        self.f = open(self.path, 'w', encoding='utf-8')
        self.f.write(f"-- ============================================================\n")
        self.f.write(f"-- 贵州省全量测试数据\n")
        self.f.write(f"-- 生成时间: {NOW}\n")
        self.f.write(f"-- 注意: 本文件有前后依赖顺序，需按整体执行\n")
        self.f.write(f"-- ============================================================\n\n")
        self.f.write("SET NAMES utf8mb4;\n")
        self.f.write("SET FOREIGN_KEY_CHECKS = 0;\n\n")
        return self

    def __exit__(self, *args):
        if self.buf:
            self._flush()
        self.f.write("\nSET FOREIGN_KEY_CHECKS = 1;\n")
        self.f.write(f"\n-- 总计生成 {self.total_rows} 条记录\n")
        self.f.close()
        print(f"\nOK 已生成: {self.path}")
        print(f"   总记录数: {self.total_rows}")

    def sql(self, s):
        self.f.write(s + '\n')

    def insert(self, table, columns, rows, chunk=50, ignore=False):
        """批量 INSERT，自动分块"""
        if not rows:
            return
        col_str = ', '.join(f'`{c}`' for c in columns)
        placeholders = ', '.join(['%s'] * len(columns))
        ignore_clause = ' IGNORE' if ignore else ''
        for i in range(0, len(rows), chunk):
            batch = rows[i:i+chunk]
            vals = []
            for row in batch:
                esc = []
                for v in row:
                    if v is None:
                        esc.append('NULL')
                    elif isinstance(v, int):
                        esc.append(str(v))
                    elif isinstance(v, float):
                        esc.append(f'{v:.8f}')
                    elif isinstance(v, datetime):
                        esc.append(f"'{v.strftime('%Y-%m-%d %H:%M:%S')}'")
                    elif isinstance(v, date):
                        esc.append(f"'{v.isoformat()}'")
                    else:
                        sv = str(v).replace("'", "''")
                        esc.append(f"'{sv}'")
                vals.append('(' + ', '.join(esc) + ')')
            self.buf.append(f"INSERT{ignore_clause} INTO `{table}` ({col_str}) VALUES\n")
            self.buf.append(',\n'.join(vals))
            self.buf.append(';\n')
            self.total_rows += len(batch)
        if len(self.buf) >= 20:
            self._flush()

    def _flush(self):
        if self.buf:
            self.f.write(''.join(self.buf))
            self.buf = []

# ===================================================================
# 4. 数据生成器
# ===================================================================

def clamp(v, lo, hi):
    return max(lo, min(hi, v))

def gauss(mu, sigma):
    return random.gauss(mu, sigma)

def pick(seq):
    return random.choice(seq)

def pick_n(seq, n):
    return random.sample(seq, min(n, len(seq)))

def rand_int(lo, hi):
    return random.randint(lo, hi)

# ── 4.1 网格生成 ──

def generate_grids(city, city_index):
    """生成城市的所有 1km 网格"""
    grids = []
    abbr = city['abbr']
    center_lat, center_lon = city['center'][1], city['center'][0]
    bbox = city['bbox']
    districts = city['districts']

    # 在城市 bounding box 内生成网格
    lat = bbox[1] + GRID_STEP
    seq = [0]  # mutable counter per district

    for _ in range(0, int((bbox[3] - bbox[1]) / GRID_SIZE) + 2):
        lat += GRID_SIZE
        if lat > bbox[3]:
            break
        lng = bbox[0] + GRID_STEP
        for _ in range(0, int((bbox[2] - bbox[0]) / GRID_SIZE) + 2):
            lng += GRID_SIZE
            if lng > bbox[2]:
                break

            # 到城市中心的距离（km）
            dlat = (lat - center_lat) * KM_PER_DEG
            dlng = (lng - center_lon) * KM_PER_DEG * math.cos(math.radians(center_lat))
            dist_km = math.sqrt(dlat*dlat + dlng*dlng)

            # 只保留城区范围（~20km半径）
            max_radius = 18 + random.uniform(-2, 4)  # per city variation
            if dist_km > max_radius:
                # 但也保留一些边缘网格
                if random.random() > 0.15:
                    continue

            # 分配区县（按距离最近的中心点）
            dist_to_centers = []
            for di, dname in enumerate(districts):
                # 用 hash 生成每个区的虚拟中心
                h = hash(dname + abbr) + di * 1000
                random.seed(h)
                dlat_offset = random.uniform(-0.08, 0.08)
                dlng_offset = random.uniform(-0.08, 0.08)
                random.seed(42)  # reset
                dc_lat = center_lat + dlat_offset
                dc_lng = center_lon + dlng_offset
                d = math.sqrt((lat-dc_lat)**2 + (lng-dc_lng)**2)
                dist_to_centers.append((d, dname))
            dist_to_centers.sort()
            district = dist_to_centers[0][1]

            # 序列号
            seq[0] += 1
            grid_code = f"GZ{abbr}{district}{seq[0]:04d}"

            grids.append({
                'grid_code': grid_code,
                'longitude': lng,
                'latitude': lat,
                'city': city['name'],
                'district': district,
                'dist_km': dist_km,
                'center_density': math.exp(-dist_km * 1.5 / max_radius),  # 0~1
            })

    # 限制网格数量
    max_grids = city['gx']
    if len(grids) > max_grids:
        # 按密度排序，保留高密度
        grids.sort(key=lambda g: g['center_density'], reverse=True)
        grids = grids[:max_grids]
        grids.sort(key=lambda g: (g['latitude'], g['longitude']))

    # 重新编号
    district_seq = {}
    for g in grids:
        key = g['district']
        district_seq[key] = district_seq.get(key, 0) + 1
        g['grid_code'] = f"GZ{abbr}{g['district']}{district_seq[key]:04d}"

    return grids

# ── 4.2 人口热力数据 ──

def generate_population_heat(grids, leaf_codes, indicators_map):
    """为每个网格的每个叶子指标生成数值"""
    rows = []

    # 找出 grid/grid_raw/grid_auto 叶子
    grid_leaf_codes = []
    for code in leaf_codes:
        ind = indicators_map.get(code)
        if ind and ind['type'] in ('grid', 'grid_raw', 'grid_auto'):
            grid_leaf_codes.append(code)

    # 分类指标生成策略
    for g in grids:
        density = g['center_density']
        base_pop = 500 + density * 3000  # 500~3500

        for code in grid_leaf_codes:
            ind = indicators_map[code]
            val = generate_population_value(code, ind, base_pop, density)
            if val is not None and val > 0:
                rows.append((g['grid_code'], code, val))

    return rows

def generate_population_value(code, ind, base_pop, density):
    """根据指标编码生成有意义的人口热力值"""
    # 工作/居住人口
    if code == 'grid_871':  # 工作人口
        return clamp(gauss(base_pop * 0.6, base_pop * 0.15), 10, 10000)
    if code == 'grid_2310':  # 居住人口
        return clamp(gauss(base_pop * 0.8, base_pop * 0.2), 10, 10000)

    # 年龄分段
    if code in ('18',): return int(gauss(base_pop * 0.08, base_pop * 0.02))
    if code == '18_24': return int(gauss(base_pop * 0.12, base_pop * 0.03))
    if code in ('2534', '25-34'): return int(gauss(base_pop * 0.25, base_pop * 0.05))
    if code in ('3544', '35-44'): return int(gauss(base_pop * 0.22, base_pop * 0.04))
    if code in ('4554', '45-54'): return int(gauss(base_pop * 0.18, base_pop * 0.04))
    if code == '55': return int(gauss(base_pop * 0.10, base_pop * 0.03))
    if code in ('55_64',): return int(gauss(base_pop * 0.08, base_pop * 0.02))
    if code in ('65',): return int(gauss(base_pop * 0.05, base_pop * 0.02))

    # 收入
    if code == '2499': return int(gauss(base_pop * 0.15, base_pop * 0.05))
    if code == '2500_3999': return int(gauss(base_pop * 0.20, base_pop * 0.05))
    if code == '40007999': return int(gauss(base_pop * 0.30, base_pop * 0.06))
    if code == '800019999': return int(gauss(base_pop * 0.22, base_pop * 0.05))
    if code == '20000': return int(gauss(base_pop * 0.08, base_pop * 0.03))

    # 教育
    if code in ('grid_8856',): return int(gauss(base_pop * 0.25, base_pop * 0.05))
    if code in ('grid_2817',): return int(gauss(base_pop * 0.15, base_pop * 0.04))
    if code == 'indicator_85178': return int(gauss(base_pop * 0.60, base_pop * 0.10))

    # 行业 (20+ codes)
    if code.startswith('it') or code == 'grid_45' or code in ('grid_654','grid_363','grid_8114','grid_7265','grid_6514','grid_7221','grid_5393','grid_1012','grid_7225','grid_2035','grid_9209','grid_6026','grid_157','grid_9145','grid_5511','grid_8268','grid_8008','grid_3747','grid_4469','grid_9997','grid_45','grid_654','grid_363','grid_8114','grid_7265','grid_6514','grid_7221','grid_5393','grid_1012','grid_7225','grid_2035','grid_9209','grid_6026','grid_157','grid_9145','grid_5511','grid_8268','grid_8008','grid_3747','grid_4469','grid_9997'):
        return int(gauss(base_pop * 0.05, base_pop * 0.02))
    if code in ('it_1',):
        return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85224',):  # 通信电子
        return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85259',):  # 法律服务
        return int(gauss(base_pop * 0.02, base_pop * 0.01))
    if code in ('indicator_85261',):  # 人力外贸
        return int(gauss(base_pop * 0.02, base_pop * 0.01))
    if code in ('indicator_85271',):  # 科学研究
        return int(gauss(base_pop * 0.02, base_pop * 0.01))

    # 职业
    if code in ('grid_8067',): return int(gauss(base_pop * 0.18, base_pop * 0.04))
    if code in ('grid_3115',): return int(gauss(base_pop * 0.15, base_pop * 0.04))
    if code in ('grid_9134',): return int(gauss(base_pop * 0.12, base_pop * 0.03))
    if code in ('grid_2332',): return int(gauss(base_pop * 0.15, base_pop * 0.04))
    if code in ('ent_2',): return int(gauss(base_pop * 0.10, base_pop * 0.03))
    if code == 'indicator_85295': return int(gauss(base_pop * 0.08, base_pop * 0.02))

    # 人生阶段
    if code in ('grid_7600',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('01',): return int(gauss(base_pop * 0.04, base_pop * 0.01))
    if code in ('13',): return int(gauss(base_pop * 0.04, base_pop * 0.01))
    if code in ('36',): return int(gauss(base_pop * 0.04, base_pop * 0.01))
    if code in ('grid_8287',): return int(gauss(base_pop * 0.05, base_pop * 0.02))
    if code in ('grid_704',): return int(gauss(base_pop * 0.04, base_pop * 0.01))
    if code in ('grid_5800',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85303', 'indicator_85313', 'indicator_85317', 'indicator_85328', 'indicator_85343', 'indicator_85353'):
        return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85303',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85313',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85317',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85328',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85343',): return int(gauss(base_pop * 0.03, base_pop * 0.01))
    if code in ('indicator_85353',): return int(gauss(base_pop * 0.03, base_pop * 0.01))

    # 资产
    if code in ('grid_9265',): return int(gauss(base_pop * 0.35, base_pop * 0.08))
    if code in ('grid_6405',): return int(gauss(base_pop * 0.40, base_pop * 0.10))

    # 消费
    if code in ('grid_6205',): return int(gauss(base_pop * 0.20, base_pop * 0.05))
    if code in ('grid_4258',): return int(gauss(base_pop * 0.45, base_pop * 0.08))
    if code in ('grid_3843',): return int(gauss(base_pop * 0.25, base_pop * 0.06))

    # grid_auto 类型（省/市/区县/常住流动人数/性别等）
    if code == 'indicator_24697': return 1  # 省
    if code == 'indicator_24746': return 1  # 市
    if code == 'indicator_24755': return 1  # 区县
    if code == 'indicator_24765': return int(base_pop)  # 常住流动人数
    if code == 'indicator_24781': return int(base_pop * 0.2)  # 流动
    if code == 'indicator_24837': return 1  # 性别（分类标记无意义）
    if code == 'indicator_24845': return int(base_pop * 0.52)  # 男
    if code == 'indicator_24852': return int(base_pop * 0.48)  # 女

    # 其他 POI 相关叶子（grid 类型下的叶子，如 grid_poi下的子节点等）
    # 这些将在 POI 阶段通过计数生成，跳过
    return None

# ── 4.3 POI 数据 ──

POI_CATEGORIES = {
    '购物中心': ['万达广场','国贸广场','南国花锦','亨特国际','星力百货','汇金星力城','荔星中心','云岩万达','花果园购物中心','奥特莱斯'],
    '超市': ['永辉超市','北京华联','沃尔玛','家乐福','合力超市','大润发','盒马鲜生'],
    '学校': ['贵州大学','贵州师范大学','贵州财经大学','贵州医科大学','贵阳一中','贵阳实验三中','贵阳六中','实验小学'],
    '医院': ['贵州省人民医院','贵州医科大学附属医院','贵阳市第一人民医院','贵阳市妇幼保健院','遵义医科大学附属医院'],
    '美食': ['老凯里酸汤鱼','贵州龙','雅园','陶然居','大东北','醉苗乡','亮欢寨','黔蘑菇'],
    '酒店': ['贵州饭店','贵阳万丽酒店','贵阳凯宾斯基','世纪金源大饭店','索菲特酒店','贵航喜来登'],
    '运动健身': ['中天健身','飞锐健身','海派健身','宝力豪健身','艾瑞健身'],
    '药店': ['一树药业','老百姓大药房','一品药业','正和祥','健一生药店'],
    '便利店': ['7-11','罗森','美宜佳','喜士多','易捷便利店'],
    '文化传媒': ['贵州日报社','贵阳日报','贵州广播电视台','贵州出版集团'],
    '市场': ['云岩区菜市场','南明区农贸市场','花溪综合市场','乌当农贸市场','白云区批发市场'],
}

def generate_poi(grids, city_name):
    """在每个城市生成 POI 数据"""
    rows = []
    poi_id = [city_name[0]]  # use as seed offset

    for g in grids:
        # 每个网格随机产生 0-3 个 POI
        poi_count = 0
        density_factor = g['center_density']
        if density_factor > 0.7:
            poi_count = rand_int(1, 4)
        elif density_factor > 0.4:
            poi_count = rand_int(0, 2)
        elif density_factor > 0.2:
            poi_count = rand_int(0, 1)
        else:
            poi_count = 1 if random.random() < 0.15 else 0

        for _ in range(poi_count):
            cat = pick(list(POI_CATEGORIES.keys()))
            poi_name = pick(POI_CATEGORIES[cat])
            # 在网格范围内偏移
            lng_offset = random.uniform(-GRID_SIZE*0.4, GRID_SIZE*0.4)
            lat_offset = random.uniform(-GRID_SIZE*0.4, GRID_SIZE*0.4)
            district = g['district']
            addr = f"{district}{pick(['中山路','中华路','人民路','北京路','延安路','解放路','西湖路','南明路','观山路','花溪大道','云岩街','乌当路'])}{rand_int(1, 200)}号"
            rows.append((f"POI_{city_name[:2]}_{len(rows)+1:04d}", poi_name,
                        round(g['longitude'] + lng_offset, 8), round(g['latitude'] + lat_offset, 8),
                        '贵州省', city_name, district, addr, cat))

    return rows

# ── 4.4 同业银行数据 ──

def generate_peer_banks(city, grids):
    """为城市生成同业银行网点"""
    rows = []
    city_name = city['name']
    bank_count = rand_int(30, 50)

    for i in range(bank_count):
        bank = pick(PEER_BANKS)
        # 在城市范围内随机选点
        bbox = city['bbox']
        lng = random.uniform(bbox[0], bbox[2])
        lat = random.uniform(bbox[1], bbox[3])
        district = pick(city['districts'])
        # 找最近的网格
        nearest = min(grids, key=lambda g: (g['longitude']-lng)**2 + (g['latitude']-lat)**2)

        org_name = f"{bank}{city_name}{pick(['分行','支行','营业部','分理处'])}"
        if bank in ('中国建设银行','中国农业银行','中国银行'):
            org_name = f"{bank}{city_name}{pick(['分行','支行'])}"

        org_code = f"PB{city['abbr']}{i+1:03d}"
        addr = f"{district}{pick(['金融街','商业街','中心路','建设路','中山路','北京路'])}{rand_int(1, 300)}号"
        rows.append((org_code, org_name, addr, round(lng, 8), round(lat, 8),
                     bank, '贵州省', city_name, district, '', nearest['grid_code']))

    return rows

# ── 4.5 网点信息 ──

def generate_branches(city, grids, city_index):
    """为城市生成工行网点"""
    rows = []
    city_name = city['name']
    count = city['branch_count']
    primary_branches = city['primary_branches']
    # branch_code 前缀: 贵阳=1, 遵义=2, 六盘水=3, 安顺=4, 毕节=5, 铜仁=6, 黔西南=7, 黔东南=8, 黔南=9
    prefix = str(city_index + 1)

    for i in range(count):
        pb = primary_branches[i % len(primary_branches)]
        # 找到网格（密度高的区域）
        candidate_grids = [g for g in grids if g['center_density'] > 0.3]
        if not candidate_grids:
            candidate_grids = grids
        g = pick(candidate_grids)

        branch_code = prefix + f"{i+1:03d}"
        secondary = f"{pb}{pick(BRANCH_SUFFIXES)}"
        if i == 0:
            secondary = f"{pb}营业部"

        staff = clamp(int(gauss(30, 12)), 8, 65)
        pm = clamp(int(gauss(staff * 0.12, 2)), 0, 10)
        cm = clamp(int(gauss(staff * 0.08, 1)), 0, 6)
        cs = clamp(int(gauss(staff * 0.20, 3)), 2, 15)
        ls = clamp(int(gauss(staff * 0.15, 2)), 1, 10)
        manager = pick(MANAGER_NAMES)
        area = clamp(gauss(400, 200), 150, 1500)
        cash_c = clamp(int(gauss(staff * 0.12, 1)), 2, 8)
        non_cash = clamp(int(gauss(staff * 0.10, 1)), 1, 6)
        m_seats = clamp(int(gauss(staff * 0.10, 1)), 1, 6)
        prop = '自有' if random.random() < 0.7 else '租赁'
        btype = pick(BRANCH_TYPES)

        district = g['district']
        street = pick(['中山路','中华路','人民路','北京路','延安路','解放路','西湖路','建设路'])
        addr = f"{district}{street}{rand_int(1, 300)}号"

        base_lng = g['longitude']
        base_lat = g['latitude']
        lng_offset = random.uniform(-GRID_SIZE*0.3, GRID_SIZE*0.3)
        lat_offset = random.uniform(-GRID_SIZE*0.3, GRID_SIZE*0.3)

        rows.append((branch_code, pb, secondary, city_name, g['grid_code'], district, street, addr,
                    round(base_lng + lng_offset, 8), round(base_lat + lat_offset, 8),
                    staff, pm, cm, cs, ls, manager, '', '', '',
                    round(area, 2), 0, cash_c, non_cash, m_seats,
                    prop, '', '', btype, '', '网点信息'))

    return rows

# ── 4.6 网点指标数据 ──

def generate_branch_indicators(branches, indicators_map, leaf_codes):
    """为每个网点生成 3 年的基础指标数据"""
    rows = []

    # 找出 branch_raw 叶子 + branch 非衍生叶子（纯输入指标）
    branch_input_codes = []
    for code in leaf_codes:
        ind = indicators_map.get(code)
        if ind and ind['type'] in ('branch_raw', 'branch') and ind['is_derived'] == '0':
            branch_input_codes.append(code)

    # Also include some parent codes that are actually stored as inputs (non-leaf branch_raw that have children)
    # The computation reads ALL 基础数据 entries, not just leaves
    all_input_codes = []
    for ind in indicators_map.values():
        if ind['type'] in ('branch_raw',) and ind['code'] not in all_input_codes:
            all_input_codes.append(ind['code'])
        if ind['type'] == 'branch' and ind['is_derived'] == '0' and ind['code'] not in all_input_codes:
            all_input_codes.append(ind['code'])

    for branch in branches:
        branch_id = branch['branch_id']
        size_factor = branch['total_staff'] / 30.0  # normalize

        for year in YEARS:
            # 年度趋势: 2022→2024 增长 3-8%
            year_factor = 1.0 + (year - 2022) * random.uniform(0.03, 0.08)

            for code in all_input_codes:
                val = generate_branch_raw_value(code, size_factor, year_factor)
                if val is not None:
                    rows.append((branch_id, year, '基础数据', code, round(val, 4)))

    return rows

def generate_branch_raw_value(code, size, year_factor):
    """生成网点原始指标值"""
    base = size * year_factor

    if code == 'interest_income':
        return clamp(gauss(base * 800, base * 150), 50, 5000)
    if code == 'branch_raw_1596':  # 手佣净收入
        return clamp(gauss(base * 80, base * 20), 5, 500)

    # 业绩表现 - 总量指标（这些是上层分类，不直接存储值）
    if code in ('branch_4568', 'branch_raw_5913'):
        return None  # 纯分类节点

    if code == 'total_asset':
        return clamp(gauss(base * 15000, base * 3000), 500, 80000)
    if code == 'branch_1201':  # 储蓄存款
        return clamp(gauss(base * 10000, base * 2000), 300, 60000)
    if code == 'corp_dep':
        return clamp(gauss(base * 5000, base * 1500), 100, 40000)
    if code == 'inst_dep':
        return clamp(gauss(base * 2000, base * 500), 50, 20000)
    if code == 'inclusive_loan':
        return clamp(gauss(base * 300, base * 80), 10, 3000)
    if code == 'personal_loan':
        return clamp(gauss(base * 500, base * 120), 10, 4000)

    if code == 'avg_balance':
        return clamp(gauss(base * 12000, base * 2500), 500, 70000)
    if code == 'avg_growth':
        return clamp(gauss(base * 300, base * 200), -1000, 3000)
    if code == 'avg_balance_2':
        return clamp(gauss(base * 8000, base * 1800), 300, 50000)
    if code == 'avg_growth_3':
        return clamp(gauss(base * 200, base * 150), -500, 2000)
    if code == 'avg_balance_1':
        return clamp(gauss(base * 4000, base * 1000), 100, 30000)
    if code == 'avg_growth_2':
        return clamp(gauss(base * 150, base * 100), -300, 1500)
    if code == 'avg_balance_3':
        return clamp(gauss(base * 1500, base * 400), 50, 15000)
    if code == 'avg_growth_1':
        return clamp(gauss(base * 100, base * 80), -200, 1000)
    if code == 'amount':
        return clamp(gauss(base * 400, base * 100), 10, 3000)
    if code == 'amount_1':
        return clamp(gauss(base * 200, base * 60), 5, 2000)

    # 客户发展
    if code == 'branch_7584':
        return None
    if code == 'branch_1512':
        return clamp(gauss(base * 3000, base * 500), 100, 15000)
    if code == 'branch_9876':
        return clamp(gauss(base * 150, base * 40), 5, 800)
    if code == 'branch_4963':
        return clamp(gauss(base * 50, base * 15), 2, 300)
    if code == 'branch_raw_8804':
        return clamp(gauss(base * 300, base * 80), 5, 2000)

    if code == '020cust':
        return clamp(int(gauss(base * 2000, base * 400)), 100, 12000)
    if code == '20600cust':
        return clamp(int(gauss(base * 600, base * 150)), 20, 4000)
    if code == '600cust':
        return clamp(int(gauss(base * 80, base * 20)), 2, 500)
    if code == 'cust50':
        return clamp(int(gauss(base * 40, base * 10)), 1, 200)
    if code == 'cust50_1':
        return clamp(int(gauss(base * 120, base * 30)), 5, 600)
    if code == '1cust':
        return clamp(int(gauss(base * 20, base * 5)), 1, 100)
    if code == '1cust_1':
        return clamp(int(gauss(base * 30, base * 8)), 2, 150)
    if code == 'branch_1274':
        return clamp(int(gauss(base * 250, base * 60)), 5, 1500)

    # 业务运营
    if code == 'branch_170':
        return None
    if code == 'countertxn':
        return clamp(int(gauss(base * 150, base * 40)), 10, 800)
    if code == 'terminaltxn':
        return clamp(int(gauss(base * 80, base * 25)), 5, 500)
    if code == 'atmtxn':
        return clamp(int(gauss(base * 40, base * 15)), 2, 300)

    return None

# ===================================================================
# 5. 主流程
# ===================================================================

def main():
    print("=" * 60)
    print("贵州省全量测试数据生成器")
    print("=" * 60)

    # 解析指标
    print("\n[1/4] 解析指标配置...")
    indicators, children_map, leaf_codes = parse_indicator_config()
    indicators_map = {ind['code']: ind for ind in indicators}

    # 初始化 SQL 写入器
    writer = SqlWriter(OUTPUT)

    with writer:
        # ── 5.1 基础系统数据 ──
        print("\n[2/4] 生成系统数据...")
        generate_system_data(writer)

        # ── 5.2 网格 → 人口热力 → POI → 同业 → 网点 ──
        print("[3/4] 生成业务数据...")
        all_grids = {}
        all_branches = []

        for ci, city in enumerate(CITY_CONFIG):
            city_name = city['name']
            print(f"  {city_name} ({city['abbr']})...")

            # 网格
            grids = generate_grids(city, ci)
            all_grids[city_name] = grids
            writer.insert('jw_grid_meta',
                ['grid_code','longitude','latitude','west_longitude','east_longitude','north_latitude','south_latitude',
                 'province','city','district','create_by','create_time','update_by','update_time'],
                [(g['grid_code'], round(g['longitude'],8), round(g['latitude'],8),
                  round(g['longitude'] - GRID_STEP, 8), round(g['longitude'] + GRID_STEP, 8),
                  round(g['latitude'] + GRID_STEP, 8), round(g['latitude'] - GRID_STEP, 8),
                  '贵州省', city_name, g['district'],
                  'admin', NOW, '', NOW) for g in grids])

            # 人口热力
            heat_rows = generate_population_heat(grids, leaf_codes, indicators_map)
            writer.insert('jw_population_heat',
                ['grid_code','indicator_code','indicator_value','create_time'],
                [(r[0], r[1], r[2], NOW) for r in heat_rows])

            # POI
            poi_rows = generate_poi(grids, city_name)
            writer.insert('jw_poi_info',
                ['org_code','poi_name','longitude','latitude','province','city','district','address','poi_type',
                 'del_flag','create_by','create_time','update_by','update_time'],
                [(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8],
                  '0', 'admin', NOW, '', NOW) for r in poi_rows])

            # 同业
            peer_rows = generate_peer_banks(city, grids)
            writer.insert('jw_peer_bank_info',
                ['org_code','org_name','org_address','longitude','latitude','bank_name',
                 'province','city','district','town','grid_code','del_flag','create_by','create_time','update_by','update_time'],
                [(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10],
                  '0', 'admin', NOW, '', NOW) for r in peer_rows])

            # 网点
            branch_rows = generate_branches(city, grids, ci)
            for br in branch_rows:
                br_entry = {
                    'branch_id': len(all_branches) + 100,
                    'branch_code': br[0], 'primary_branch': br[1], 'secondary_branch': br[2],
                    'city': br[3], 'grid_code': br[4], 'district_name': br[5], 'street': br[6],
                    'address': br[7], 'longitude': br[8], 'latitude': br[9],
                    'total_staff': br[10], 'personal_manager': br[11], 'corporate_manager': br[12],
                    'counter_staff': br[13], 'lobby_staff': br[14], 'branch_manager': br[15],
                    'total_area': br[18], 'cash_counter': br[20], 'non_cash_counter': br[21],
                    'manager_seat': br[22], 'property_right': br[24], 'branch_type': br[27],
                }
                all_branches.append(br_entry)

            writer.insert('jw_branch_info',
                ['branch_id','branch_code','primary_branch','secondary_branch','city','grid_code','district_name','street',
                 'address','longitude','latitude',
                 'total_staff','personal_manager','corporate_manager','counter_staff','lobby_staff',
                 'branch_manager','manager_tenure','manager_resume','manager_history',
                 'total_area','other_floor_area','cash_counter','non_cash_counter','manager_seat',
                 'property_right','lease_expire','last_renovation','branch_type','relocation','data_source',
                 'del_flag','create_by','create_time','update_by','update_time'],
                [(br_entry['branch_id'], r[0], r[1], r[2], r[3], r[4], r[5], r[6],
                  r[7], r[8], r[9],
                  r[10], r[11], r[12], r[13], r[14],
                  r[15], r[16], r[17], r[18],
                  r[19], r[20], r[21], r[22], r[23],
                  r[24], r[25], r[26], r[27], r[28], r[29],
                  '0', 'admin', NOW, '', NOW) for r, br_entry in zip(branch_rows, all_branches[-len(branch_rows):])])

            print(f"   网格:{len(grids)} 人口热力:{len(heat_rows)} POI:{len(poi_rows)} 同业:{len(peer_rows)} 网点:{len(branch_rows)}")

        # ── 5.3 网点指标 ──
        print("[4/4] 生成网点指标数据...")
        total_bi = 0
        for city_name, branches_group in group_by_city(all_branches).items():
            bi_rows = generate_branch_indicators(branches_group, indicators_map, leaf_codes)
            writer.insert('jw_branch_indicator',
                ['branch_id','data_year','sheet_type','indicator_code','indicator_value','create_time'],
                [(r[0], r[1], r[2], r[3], r[4], NOW) for r in bi_rows])
            total_bi += len(bi_rows)
            print(f"  {city_name}: {len(bi_rows)} 条指标数据")
        print(f"  网点指标总计: {total_bi}")

        # ── 5.4 尾部说明 ──
        writer.sql("\n-- ============================================================")
        writer.sql("-- 验证查询")
        writer.sql("-- ============================================================")
        writer.sql("SELECT '网格数' AS k, COUNT(*) AS v FROM jw_grid_meta UNION ALL")
        writer.sql("SELECT '人口热力', COUNT(*) FROM jw_population_heat UNION ALL")
        writer.sql("SELECT 'POI', COUNT(*) FROM jw_poi_info UNION ALL")
        writer.sql("SELECT '同业', COUNT(*) FROM jw_peer_bank_info UNION ALL")
        writer.sql("SELECT '网点', COUNT(*) FROM jw_branch_info UNION ALL")
        writer.sql("SELECT '网点指标', COUNT(*) FROM jw_branch_indicator;")

        # 汇总
        total = writer.total_rows
        print(f"\n{'='*60}")
        print(f"OK 生成完成!")
        print(f"   总记录数: {total}")
        print(f"   输出文件: {OUTPUT}")
        print(f"{'='*60}")

def group_by_city(branches):
    groups = {}
    for b in branches:
        groups.setdefault(b['city'], []).append(b)
    return groups

# ── 5.1 系统数据 ──

def generate_system_data(writer):
    """生成部门、用户、角色关联等系统数据"""
    global NEXT_DEPT_ID, NEXT_USER_ID

    # ── sys_role (data_reviewer, 幂等) ──
    writer.insert('sys_role',
        ['role_id','role_name','role_key','role_sort','data_scope','menu_check_strictly','dept_check_strictly','status','del_flag','create_by','create_time','update_by','update_time','remark'],
        [(3, '数据审核员', 'data_reviewer', 3, 2, 1, 1, '1', '0', 'admin', NOW, '', None, '可审批跨机构数据查看申请')],
        ignore=True)

    # ── sys_dept ──
    print("  部门/用户/角色...")

    dept_rows = []

    # 已有的 dept_id 200-258 已在 seed 数据中，新增 259+
    # 省行 - 跳过(已有200)

    # 为每个城市生成 一级支行(市行) 和 二级支行(网点级) 部门
    city_dept_ids = {}
    primary_dept_ids = {}
    outlet_dept_ids = {}

    for ci, city in enumerate(CITY_CONFIG):
        # 市行（如果已有则跳过）
        city_dept_id = 201 + ci  # 201-209
        if ci >= 9:
            city_dept_id = NEXT_DEPT_ID
            NEXT_DEPT_ID += 1

        city_dept_ids[city['name']] = city_dept_id

        if ci >= 9:  # 只需记录，不重复插入已有
            dept_rows.append((city_dept_id, 200, f'0,200', city['name'], ci+1,
                            city['name'].replace('市','').replace('州',''), '', '', '0', '0'))

        # 一级支行
        for pi, pb in enumerate(city['primary_branches']):
            if pb == '贵阳分行' and ci == 0:
                pid = 210
            elif pb == '遵义分行' and ci == 1:
                pid = 220
            elif pb == '六盘水分行' and ci == 2:
                pid = 230
            elif pb == '清镇市支行':
                pid = 211
            elif pb == '仁怀市支行':
                pid = 221
            elif pb == '盘州市支行':
                pid = 231
            elif pb == '赤水市支行':
                pid = 222
            elif pb == '正安县支行':
                pid = 223
            elif pb == '开阳县支行':
                pid = 212
            elif pb == '修文县支行':
                pid = 213
            elif pb == '息烽县支行':
                pid = 214
            else:
                pid = NEXT_DEPT_ID
                NEXT_DEPT_ID += 1

            primary_dept_ids[pb] = pid
            if pid > 258:  # 只插入新增的
                dept_rows.append((pid, city_dept_id, f'0,{200 if ci < 9 else city_dept_id},{city_dept_id}',
                                pb, pi+1, pb.replace('分行','').replace('支行','').replace('市',''),
                                '', '', '0', '0'))

        # 网点级部门（二级支行 - 每个 primary_branch 下虚拟一些网点）
        # 现有 outlet dept 232-258，新增从 NEXT_DEPT_ID 开始
        outlet_count = rand_int(2, 5)
        for oi in range(outlet_count):
            pid = NEXT_DEPT_ID
            NEXT_DEPT_ID += 1
            # 找一个 primary_branch
            pb = pick(city['primary_branches'])
            pb_dept_id = primary_dept_ids[pb]
            outlet_name = f"{pb}{pick(['第一分理处','第二分理处','第三分理处','开发区支行','新区支行','营业部'])}"
            dept_rows.append((pid, pb_dept_id, f'0,200,{city_dept_id},{pb_dept_id}',
                            outlet_name, oi+1, pick(MANAGER_NAMES), '', '', '0', '0'))

    if dept_rows:
        writer.insert('sys_dept',
            ['dept_id','parent_id','ancestors','dept_name','order_num','leader','phone','email','status','del_flag'],
            dept_rows, ignore=True)

    # ── sys_user ──
    user_rows = []
    user_role_rows = []
    role_dept_rows = []

    # 每个城市3个用户（普通员工、支行级审核员）
    for ci, city in enumerate(CITY_CONFIG):
        city_dept_id = city_dept_ids[city['name']]

        # 普通员工（所属市行）
        uid = NEXT_USER_ID
        NEXT_USER_ID += 1
        username = f"staff_{city['abbr'].lower()}"
        nickname = f"员工-{city['name']}"
        phone = f"139{rand_int(10000000, 99999999)}"
        user_rows.append((uid, city_dept_id, username, nickname, '00', '', phone, '1', '',
                         BCRYPT, '0', '0', '127.0.0.1', NOW, NOW, 'admin', NOW, '', None,
                         f'测试：{city["name"]}普通员工'))
        user_role_rows.append((uid, '2'))

        # 支行级审核员
        uid2 = NEXT_USER_ID
        NEXT_USER_ID += 1
        username2 = f"reviewer_{city['abbr'].lower()}"
        nickname2 = f"审核-{city['name']}"
        phone2 = f"138{rand_int(10000000, 99999999)}"
        user_rows.append((uid2, city_dept_id, username2, nickname2, '00', '', phone2, '1', '',
                         BCRYPT, '0', '0', '127.0.0.1', NOW, NOW, 'admin', NOW, '', None,
                         f'测试：{city["name"]}数据审核员'))
        user_role_rows.append((uid2, '3'))

        # 添加到 role_dept (data_reviewer → this city dept)
        role_dept_rows.append((3, city_dept_id))

    # 省行级审核员
    uid_prov = NEXT_USER_ID
    NEXT_USER_ID += 1
    user_rows.append((uid_prov, 200, 'reviewer_province', '审核-贵州省分行',
                     '00', '', '13800000103', '1', '',
                     BCRYPT, '0', '0', '127.0.0.1', NOW, NOW, 'admin', NOW, '', None,
                     '测试：贵州省分行数据审核员'))
    user_role_rows.append((uid_prov, '3'))

    # 省行级 role_dept
    role_dept_rows.append((3, 200))

    if user_rows:
        writer.insert('sys_user',
            ['user_id','dept_id','user_name','nick_name','user_type','email','phonenumber','sex','avatar',
             'password','status','del_flag','login_ip','login_date','pwd_update_date','create_by','create_time','update_by','update_time','remark'],
            user_rows)

    if user_role_rows:
        writer.insert('sys_user_role', ['user_id', 'role_id'], user_role_rows)

    if role_dept_rows:
        writer.insert('sys_role_dept', ['role_id', 'dept_id'], role_dept_rows, ignore=True)

if __name__ == '__main__':
    main()
