# 签到助手（Android）

把「打开微信 → 进公众号 → 点链接 → 等页面加载 → 点签到」这一串操作，压缩成一次点击或每天定时自动完成。

定位用的是手机真实 GPS，不做任何位置伪造。

## 功能

- **每日自动签到**：WorkManager 定时任务，到点自动取定位并提交，成功后发一条低打扰通知，失败发高优先级通知
- **启动补签**：打开 App 时若今天还没签，自动跑一次
- **凭证保活**：账号密码用 Android Keystore（AES-256-GCM）加密存本地，token 到期前自动重新登录，无需手动干预
- **手动签到**：首页一键提交，附距离与半径提示
- **签到记录**：本地 Room 落库，按日期查看历史
- **接口可配置**：签到地址、请求体模板、成功判定关键字都能在设置里改

## 技术栈

Kotlin · Jetpack Compose（Material 3）· Hilt · Room · DataStore · WorkManager · OkHttp
minSdk 24 / targetSdk 34 / AGP 8.5.2 / Kotlin 2.0

## 构建

接口域名不在源码里，通过 `local.properties` 注入：

```bash
cp local.properties.example local.properties
```

然后按需填写：

```properties
sdk.dir=/path/to/Android/Sdk
api.base=https://api.example.com/api
h5.base=https://example.com
```

不填也能编译（会回落到占位地址），只是运行时请求会失败。

```bash
./gradlew assembleDebug
```

## 权限

| 权限 | 用途 |
| --- | --- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 取当前坐标 |
| `ACCESS_BACKGROUND_LOCATION` | 后台定时签到时取坐标（Android 10+ 需选「始终允许」） |
| `POST_NOTIFICATIONS` | 签到结果通知 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 避免国产 ROM 杀后台导致定时任务不执行 |

## 已知限制

- 后台任务受系统 Doze 和厂商省电策略影响，建议手动加白名单
- 服务端 token 有效期 24 小时且没有续期接口，靠定时重新登录维持
- 本项目只做「少点几下」，不绕过任何服务端校验，也不修改定位数据

## 说明

仓库内不含任何真实域名、账号、token 或抓包数据。`local.properties`、`*.har` 等已在 `.gitignore` 中排除。
