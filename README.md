# 进京证助手 · 交接文档（v2.2）

## 一、项目概况
- **用途**：小米 17 Ultra 桌面小组件，显示进京证剩余进度（漏斗液面）。
- **流程**：相册截图 → 分享/识别（ML Kit 中文 OCR）→ 确认存本机（SharedPreferences）→ 1×2 竖向小组件显示：百分比 / 剩余天数 / 到期日(带星期) / 年度余次（六环内 12 次/年）→ 到期前 2 天 09:00 通知提醒。
- **包名**：`com.jjz.assist`；compileSdk 35 / minSdk 31 / targetSdk 35；AGP 8.7.2 / Kotlin 2.0.21。
- **唯一依赖**：`com.google.mlkit:text-recognition-chinese:16.0.0`（中文 OCR）。

## 二、源码位置
- **GitHub（私有）**：https://github.com/zhbin426/jjz-assist （main 分支，含全部 Kotlin/XML 源码与工程配置）
- **本地完整工程**：`JJZHelper/`（zip 包：`进京证助手-源码.zip`）
- **注意**：GitHub 上**不含** `app/src/main/res/drawable-nodpi/glass_rim.png`（320×640 二进制玻璃边贴图，接口只推文本）。克隆后需从 zip 里把该文件复制到同一路径，否则编译时 ShapeArt 会跳过玻璃层（不报错，但没玻璃质感）。
- 当前 versionCode=1 / versionName=1.0 未随版本递增，每次出新包建议手动 +1。

## 三、文件结构（13 个源文件）
| 文件 | 职责 |
|---|---|
| MainActivity.kt | 主界面：数据卡片、相册选图、直接读最新截图、手改生效/到期日、次数管理、外观设置（透明度滑杆+色板）、效果预览 |
| ShareReceiverActivity.kt | 接收分享图片 → OCR → 确认页 → 保存 |
| OcrEngine.kt | 三路识别（原图/放大1.8x/灰度增强）取并集 |
| OcrParser.kt | 从 OCR 文本抽日期（年月日/紧凑/月日/斜杠 4 种正则 + "有效期至"关键词优先） |
| ImageLoader.kt | 分享图先复制到 cache 再解码（不依赖临时授权） |
| PassStore.kt | SharedPreferences 存储：有效期、总天数、年度次数、液面颜色/透明度 |
| PassWidgetProvider.kt | 组件核心：盒子排版公式（见下）+ RemoteViews 下发 |
| WidgetRenderer.kt | App 内预览用位图渲染，与组件同公式（px 单位镜像） |
| ShapeArt.kt | 卡片绘制：rich()提饱和 → 圆角底(alpha=前景1/4) → 液面渐变+水面亮线 → 顶光 → 玻璃边贴图(alpha=200) |
| PaletteView.kt | 逐像素 360×1 色相条取色板（所见即所得） |
| ReminderScheduler/Receiver.kt | 两个闹钟：到期前 2 天 09:00 提醒；到期次日 00:10 刷成 0% |
| BootReceiver.kt | 开机/覆盖安装后重刷组件、重排闹钟 |
| widget_pass.xml | FrameLayout + art(ImageView fitXY) + 五个 TextView（autoSize，位置全部由代码下发） |

## 四、v2.2 排版公式（改动核心，勿随手调）
四行字=固定高度盒子、顶部对齐，`setViewLayoutHeight(DIP)+setViewPadding(px)` 定位（单位 dp）：
```
tPad=(h*0.035)coerceIn(3,6)   tH=(h*0.105)coerceIn(11,16)   pH=(h*0.24)coerceIn(18,34)
dH=(h*0.125)coerceIn(11,17)   eH=qH=(h*0.115)coerceIn(10,15)
qPad=(h*0.055)coerceIn(5,9)   gap=max(h*0.018,1.5)
quotaTop=h-qPad-qH   dateTop=quotaTop-gap-eH   bottomLimit=dateTop-gap   topLimit=tPad+tH+gap
surface=h*(1-pct/100)（过期取 0.42h）
pctTop=(surface-1.12pH)coerceIn(topLimit, max(bottomLimit-pH-gap-dH, topLimit))
daysTop=min( max(pctTop+pH+gap, surface+0.025h), dateTop-dH )   ← 硬上限=到期日上沿
```
**铁律**：所有 coerceIn 前先保证下限≤上限（v2.2 之前的闪退就是 coerceIn(564,444) 抛 IllegalArgumentException，低百分比触发）。已用 Python 模拟 h=85–260dp × pct=0–100 每 2% 验证 0 重叠。改公式请同步改 PassWidgetProvider（dp）和 WidgetRenderer（px）两处，并重跑模拟。

## 五、编译
```
cd JJZHelper && echo "sdk.dir=/home/user/tools/android-sdk" > local.properties
env -u http_proxy -u https_proxy -u HTTP_PROXY -u HTTPS_PROXY -u PROXY -u proxy \
  JAVA_HOME=/home/user/tools/jdk17 ANDROID_HOME=/home/user/tools/android-sdk \
  nohup /home/user/tools/gradle/bin/gradle assembleDebug --no-daemon --console=plain > /tmp/build.log 2>&1 &
# 产物 app/build/outputs/apk/debug/app-debug.apk（约45MB）；交付后 rm local.properties
```
- 必须 unset 代理，否则 Maven Central 挂死；约 1 分钟。
- 预览图重出：`python3 预览生成.py`（与组件同公式，出红/蓝/低水位三张图）。

## 六、已定口径（勿推翻）
- 背景不透明度 = 前景 1/4；天数=生效日 0 点至到期日 24 点按秒算；百分比一位小数；到期日带星期；色板取色而非固定色点。
- 组件文字用**原生 TextView**（整张位图画字已被否决：压扁+糊）；**不做动画**（RemoteViews 限制，用户已认可"做不了就不动"）。
- 视觉：自画玻璃被三轮否决，最终方案=代码画底色液面 + 真实渲染玻璃边贴图（glass_rim.png，alpha=200）+ rich() 提饱和 ×1.35。

## 七、已知未决
- v2.2 三个修复（重叠/闪退/饱和度）待真机验收，尤其 1×2 格子 fitXY 拉伸后的实际比例。
- minSdk 31 意味着 Android 12 以下不能装，用户未表态。
- 记忆力恢复仪（另一插曲线，ShiBeiApp）尚未迁移 GitHub；其豆包应用部署页需登录抓取，仍挂起。
