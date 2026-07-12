/**
 * 百度地图 GL API 动态加载器
 * 单例 Promise 模式，确保只加载一次
 */
const AK = 'g0o18DfiUGEiOg9QbZ6Cq4N5QgtHX4tr'
const BDMAP_API_URL = `https://api.map.baidu.com/api?v=3.0&type=webgl&ak=${AK}&callback=onBMapGLReady`

let loadPromise = null

/**
 * 加载百度地图 GL SDK，返回 Promise<BMapGL>
 * 可多次调用，只会加载一次
 */
export function loadBMapGL() {
  if (window.BMapGL) {
    return Promise.resolve(window.BMapGL)
  }
  if (loadPromise) {
    return loadPromise
  }

  loadPromise = new Promise((resolve, reject) => {
    // 注册全局回调（百度 SDK 的 JSONP 机制）
    window.onBMapGLReady = function () {
      if (window.BMapGL) {
        resolve(window.BMapGL)
      } else {
        reject(new Error('BMapGL 未在回调后可用'))
      }
      // 清理全局回调
      delete window.onBMapGLReady
    }

    const script = document.createElement('script')
    script.src = BDMAP_API_URL
    script.async = true
    script.onerror = function () {
      delete window.onBMapGLReady
      loadPromise = null
      reject(new Error('百度地图 GL SDK 加载失败'))
    }
    document.head.appendChild(script)

    // 超时保护（15秒）
    setTimeout(() => {
      if (!window.BMapGL) {
        delete window.onBMapGLReady
        loadPromise = null
        reject(new Error('百度地图 GL SDK 加载超时'))
      }
    }, 15000)
  })

  return loadPromise
}

/**
 * 判断 SDK 是否已加载完成
 */
export function isBMapGLLoaded() {
  return !!window.BMapGL
}
