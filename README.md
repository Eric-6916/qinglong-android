# 青龙面板 Android 客户端

> 用 Jetpack Compose 打造的第三方 [青龙面板](https://github.com/whyour/qinglong) 移动端管理工具 —— 任务、脚本、环境变量、订阅、依赖、日志，一掌可控。

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-1.2.3-blue">
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-24-green">
  <img alt="Target SDK" src="https://img.shields.io/badge/targetSdk-35-green">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.24-purple">
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material3-orange">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-lightgrey">
</p>

---

## ✨ 功能特性

### 🖥 多面板管理
- 同时管理多个青龙面板，一键切换，各自保留登录状态
- 添加面板失败自动完整回滚，激活面板防误删

### 📊 仪表盘
- Bento 网格总览：今日运行次数、运行中 / 排队、成功率、平均耗时
- 近 7 天运行趋势图、服务器负载与运行时长实时信息

### ⏰ 定时任务
- 任务增删改查、运行 / 停止、启用 / 禁用、标签管理
- 五态语义状态色（运行中 / 排队 / 已禁用 / 失败 / 空闲），实时查看任务日志

### 📜 脚本管理
- 目录树浏览、脚本上传 / 在线编辑 / 重命名 / 删除
- 手动运行 / 停止，运行日志直达

### 🔐 环境变量
- 增删改查、启用 / 禁用、置顶、拖动排序
- 支持 JSON 环境变量文件批量上传

### 🔄 订阅管理
- 订阅增删改查、运行 / 停止、启用 / 禁用、历史日志查看

### 📦 依赖管理
- nodejs / python3 / linux 依赖查看、安装、卸载、重装、取消

### ⚙️ 系统与配置
- 配置文件在线编辑（`/ql/config`）
- 日志目录浏览、日志查看与删除、系统日志
- 面板标题、日志清理频率、任务并发、依赖代理、各类镜像源设置
- 通知推送测试

### 🎨 设计
- 「晨曜极光 Aurora Dawn」浅色主题：四色 mesh 极光背景 + 液态玻璃卡片 + 玉青主色
- 深色极光主题，全组件 WCAG 2.1 AA 对比度达标
- 全站顶栏透明化，沉浸式极光体验

### 🔒 安全
- 凭据经 EncryptedSharedPreferences 加密存储
- 双认证模式：**账号密码登录**（支持两步验证 2FA）/ **应用授权**（client_id + client_secret）
- 会话过期自动感知，一键重连兜底

---

## 📱 截图

> 待补充：欢迎在 `docs/images/` 下放置应用截图后在下方引用。

| 首页仪表盘 | 任务列表 | 脚本管理 |
|:---:|:---:|:---:|
| _screenshot_ | _screenshot_ | _screenshot_ |

---

## 🛠 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 1.9.24 |
| UI | Jetpack Compose（Material3，100% Compose 无 XML 布局） |
| 架构 | MVVM + Repository + Navigation Compose |
| 网络 | Retrofit + OkHttp + Gson |
| 存储 | DataStore（设置）+ EncryptedSharedPreferences（凭据） |
| 构建 | Gradle KTS，AGP 8.5.2，JDK 17 |
| 兼容 | minSdk 24（Android 7.0）~ targetSdk 35（Android 15） |

---

## 📦 构建与安装

### 环境要求
- JDK 17+
- Android SDK（compileSdk 35）
- Gradle 通过仓库自带 Wrapper 管理，无需单独安装

### 构建 Debug 包

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
（v1.2.2 起与 release **统一 applicationId `com.qinglong.panel`** 且同为正式签名——debug/release 互为覆盖升级，登录信息保留；同机只能安装其中一个）

### 构建 Release 包

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

**签名说明**：Release 签名读取项目根目录的 `keystore.properties`（**该文件不入库，见 `.gitignore`**）：

```properties
storeFile=app/release.keystore
storePassword=<你的密钥库密码>
keyAlias=<别名>
keyPassword=<你的密钥密码>
```

- 本地已有签名配置时直接产出正式签名包
- **未配置时自动回退 debug 签名**，保证 `assembleRelease` 始终可构建（便于 CI 或他人 clone 后验证）
- 自行打包请把生成的 keystore 放入 `app/` 并在 `keystore.properties` 中引用；`.gitignore` 已忽略 `*.keystore` / `*.jks`，不会误提交

---

## 🚀 使用说明

1. **首次启动**：进入配置向导，填写青龙面板地址（如 `http://192.168.1.10:5700`）
2. **选择认证方式**：
   - `账号密码`：使用面板登录账号（支持两步验证，推荐）
   - `应用授权`：在面板「系统设置 → 应用设置」中创建 client_id / client_secret
3. 连接成功后进入首页，底部五个 Tab：**首页 / 任务 / 脚本 / 环境 / 订阅**，更多功能从首页右上角进入 **设置中心**

---

## 📂 目录结构

```
app/src/main/java/com/qinglong/panel/
├── data/
│   ├── local/          # SecureStore（凭据加密）、SettingsStore（DataStore）
│   ├── model/          # 数据模型
│   ├── remote/         # ApiService（青龙 API）、ApiClient、TokenManager
│   └── repository/     # QinglongRepository（单一数据源）
├── di/                 # AppContainer（手动依赖注入）
├── ui/
│   ├── component/      # 玻璃卡、状态胶囊、极光背景等通用组件
│   ├── dashboard/      # 仪表盘
│   ├── cron/           # 定时任务列表 / 编辑
│   ├── script/         # 脚本列表 / 详情 / 上传
│   ├── env/            # 环境变量列表 / 编辑
│   ├── sub/            # 订阅列表 / 编辑
│   ├── dependence/     # 依赖管理
│   ├── configs/        # 配置文件
│   ├── logfiles/       # 日志目录 / 系统日志
│   ├── log/            # 日志查看器
│   ├── system/         # 系统配置
│   ├── settings/       # 设置中心 / 面板管理
│   ├── setup/          # 首次配置向导
│   ├── theme/          # Aurora 主题（色板 / 排版 / 状态色）
│   └── nav/            # 路由表
└── MainActivity.kt
```

---

## 🗺 更新日志

### v1.2.3（当前版本）
- 🎨 日志系列页面（日志目录 / 任务日志 / 订阅日志 / 系统日志）背景统一为全站四色 mesh 极光，顶部颜色与首页、任务页一致

### v1.2.2
- 🐛 修复换包丢失登录信息：debug/release 统一 applicationId 与签名，互为覆盖升级登录态保留
- 🐛 修复冷启动时 TokenManager 缓存可能载入错误面板 token 的竞态（多面板场景误报"登录已过期"）

### v1.2.1
- 🔴 任务状态色五态化：**禁用状态由灰改红**，新增中性空闲色；暗色下运行 / 失败态提亮至 WCAG AA 达标
- 🎨 全站 17 个页面顶栏透明化（参考首页），滚动后不再露白条
- 📦 构建产物由 debug 包切换为 **release 签名包**

### v1.2.0
- 浅色主题重设计「晨曜极光 Aurora Dawn」：四色 mesh 极光 + 液态玻璃 + Bento 仪表盘
- 输入法遮挡修复（软键盘升起自动滚入可见区域）
- 移除禁截屏限制，允许截图分享
- 四个列表页悬浮按钮上移避开底部导航

### v1.1.0
- 多青龙面板管理：添加 / 切换 / 删除，各自保留登录态，失败自动回滚
- 启动按会话恢复结果分流，已存凭据一键重连

---

## ⚠️ 免责声明

本项目为**第三方非官方**青龙面板客户端，与青龙面板官方无任何关联。仅供个人学习与管理自有服务器使用，请勿用于非法用途。使用本软件产生的一切后果由使用者自行承担。

---

## 📄 License

本项目基于 [MIT License](LICENSE) 开源。

---

## ❤️ 赞赏支持

如果这个项目对你有帮助，欢迎扫码赞赏，你的支持是持续维护的动力：

<div align="center">
  <img src="docs/images/reward-qr.jpg" width="280" alt="赞赏码" />
</div>
