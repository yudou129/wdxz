/**
 * 银行名称 -> SVG图标 映射
 *
 * 从 bank-icons 目录自动加载所有SVG，通过银行中文名查找对应图标URL。
 * 用于地图标记将文字标记替换为银行Logo。
 */

// 自动加载 bank-icons 目录下所有 SVG
const bankSvgs = {}
const ctx = require.context('@/views/jwmap/shared/assets/bank-icons', false, /\.svg$/)
ctx.keys().forEach(key => {
  const name = key.replace(/^\.\//, '').replace(/\.svg$/, '')
  bankSvgs[name] = ctx(key)
})

// 银行中文名 -> SVG 文件名映射
const BANK_SVG_MAP = {
  // 国有大型银行
  '中国工商银行': 'icbc-01',
  '工商银行': 'icbc-01',
  '中国农业银行': 'abc-5',
  '农业银行': 'abc-5',
  '中国建设银行': 'ccb-2',
  '建设银行': 'ccb-2',
  '建设银行股份有限公司': 'ccb-2',
  '交通银行': 'comm-1',
  '中国邮政储蓄银行': 'psbc-1',
  '邮储银行': 'psbc-1',

  // 全国性股份制商业银行
  '招商银行': 'cmb-3',
  '中信银行': 'citic-3',
  '浦发银行': 'spdb-2',
  '中国民生银行': 'cmbc-1',
  '民生银行': 'cmbc-1',
  '兴业银行': 'cib-2',
  '中国光大银行': 'ceb-2',
  '光大银行': 'ceb-2',
  '平安银行': 'spabank-1',
  '华夏银行': 'hxbank-1',
  '广发银行': 'gdb-1',

  // 城商行
  '北京银行': 'bjbank-1',
  '上海银行': 'shbank-1',
  '南京银行': 'njcb',
  '江苏银行': 'jsbank-1',
  '杭州银行': 'hzcb',
  '宁波银行': 'nbbank-1',
  '贵阳银行': 'gycb',
  '贵州银行': 'bank-of-guizhou-portfolio',
  '渤海银行': 'bhb',
  '浙商银行': 'czbank-2',
  '漳州银行': 'gzb',

  // 农商行
  '上海农商银行': 'srcb',
  '上海农村商业银行': 'srcb',
}

/**
 * 根据银行中文名获取对应的SVG图标URL
 * @param {string} bankName - 银行中文名（如"中国建设银行"）
 * @returns {string|null} SVG图片URL，找不到返回null
 */
export function getBankSvgUrl(bankName) {
  if (!bankName) return null
  const name = bankName.trim()

  // 精确匹配
  if (BANK_SVG_MAP[name] !== undefined) {
    const slug = BANK_SVG_MAP[name]
    return bankSvgs[slug] || null
  }

  // 模糊匹配（包含关系）
  for (const key of Object.keys(BANK_SVG_MAP)) {
    if (name.includes(key)) {
      const slug = BANK_SVG_MAP[key]
      return bankSvgs[slug] || null
    }
  }

  return null
}

/**
 * 获取本行（自有网点）的SVG图标URL
 * 默认使用贵州银行图标，可通过修改此处更换
 */
export function getOwnBankSvgUrl() {
  return bankSvgs['icbc-01'] || null
}
