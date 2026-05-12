# 船厂离线采集平台 API 维护排障手册

## 1. 文档目的

本文档用于给维护人员、测试人员、实施人员定位问题时使用。

核心目标：

- 当 APP 或后台页面出问题时，能快速判断是哪一个 API 出错
- 能区分是前端问题、网络问题、鉴权问题，还是后端 API 问题
- 能根据报错现象快速找到应该看的接口、日志和排查动作

说明：

- 当前文档基于 2026-05-12 项目代码整理
- 当前“真氚云接入”尚未完成，上传成功是以后端当前网关返回结果为准

---

## 2. 系统结构总览

当前系统有 3 条主要调用链：

1. 安卓 APP
2. 管理后台 `admin-web`
3. 后端 `backend`

逻辑关系如下：

```text
安卓 APP / 管理后台
        |
        v
   后端 REST API
        |
        v
 当前模拟氚云网关 / 未来真实氚云
```

默认地址：

- 后端：`http://localhost:8080/api`
- 管理后台：`http://localhost:5173`
- 安卓模拟器访问后端：`http://10.0.2.2:8080/api`

代码参考：

- 后端端口配置：[application.yml](/home/sm6/project/backend/src/main/resources/application.yml:1)
- 安卓 API 地址：[build.gradle.kts](/home/sm6/project/android-app/app/build.gradle.kts:20)

---

## 3. 先判断是“哪一类问题”

出现故障时，先按下面顺序判断：

### 3.1 后端是否存活

先检查：

- 后端进程是否启动
- `8080` 端口是否在监听
- `GET /actuator/health` 是否能返回

如果后端都没起来，后面的所有 API 都会失败。

### 3.2 是否是网络或地址问题

典型现象：

- 安卓端提示无法登录、无法上传，但后端本身正常
- 管理后台页面打开了，但所有请求失败

优先检查：

- 安卓端是不是还在连 `10.0.2.2:8080`
- 真机是否误用了模拟器地址
- 管理后台的 `VITE_API_BASE_URL` 是否正确

### 3.3 是否是鉴权问题

典型现象：

- 登录后很快又提示未登录
- 后台点用户列表时报 401 / 403
- 上传时报“当前账号没有上传权限”

优先检查：

- `Authorization: Bearer <token>` 是否带上
- token 是否过期
- 账号是否被禁用
- 当前账号是否有管理员、上传、表单权限

### 3.4 是否是业务接口问题

典型现象：

- 登录正常，但上传失败
- 分片上传到一半中断
- 所有文件都传完了，但最后没确认成功
- 后台能登录，但用户列表、权限修改失败

此时就进入下面的 API 映射排查表。

---

## 4. 安卓 APP 功能与 API 对照表

## 4.1 登录

### 功能现象

- 用户输入手机号和密码登录

### 对应 API

- `POST /api/auth/login`

### 调用方

- 安卓端：[HttpCollectorApi.kt](/home/sm6/project/android-app/app/src/main/java/com/shipyard/collector/data/remote/HttpCollectorApi.kt:25)

### 成功结果

- 返回 token
- 返回用户权限
- 返回表单列表

### 常见失败表现

- “手机号或密码错误”
- “首次登录需要联网”但实际无法登录
- 管理员后台登录失败

### 常见原因

- 账号不存在
- 密码错误
- 账号被禁用
- 后端没启动
- 安卓端地址配错

### 后端典型错误

- `401 UNAUTHORIZED`
- `手机号或密码错误`
- `账号不可用`
- `登录态不存在`
- `登录态已过期`

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:39)
- 服务：[AuthService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/AuthService.java:33)

---

## 4.2 上传前初始化

### 功能现象

- 点击“开始上传”后，上传任务刚开始就失败
- 一条记录还没真正开始传文件就报错

### 对应 API

- `POST /api/uploads/init`

### 调用方

- 安卓端：[HttpCollectorApi.kt](/home/sm6/project/android-app/app/src/main/java/com/shipyard/collector/data/remote/HttpCollectorApi.kt:110)

### 这个接口负责什么

- 校验当前账号是否能上传
- 校验当前账号是否有该表单权限
- 校验图片数量和语音数量是否合法
- 创建或恢复一条上传会话
- 为每个附件建立分片上传状态

### 常见失败表现

- 一点击上传就失败
- APP 提示无上传权限
- APP 提示没有该表单权限
- 修改过本地记录后再次上传时异常

### 常见原因

- 当前账号没有上传权限
- 当前账号没有该表单权限
- 图片数量不在 `1-5` 张范围内
- 语音数量超过 1 条
- 附件角色或元数据异常

### 后端典型错误

- `403 FORBIDDEN`：`当前账号没有上传权限`
- `403 FORBIDDEN`：`当前账号没有该表单权限`
- `400 BAD_REQUEST`：`单条记录需要 1 到 5 张图片`
- `400 BAD_REQUEST`：`单条记录最多 1 条语音`
- `400 BAD_REQUEST`：`发现不支持的附件角色`
- `409 CONFLICT`：`该记录已归属于其他账号`

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:64)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:47)

---

## 4.3 分片上传

### 功能现象

- 上传进度卡住
- 上传到一半失败
- 某个文件一直重试

### 对应 API

- `POST /api/uploads/{recordId}/files/{fileId}/chunks`

### 调用方

- 安卓端：[HttpCollectorApi.kt](/home/sm6/project/android-app/app/src/main/java/com/shipyard/collector/data/remote/HttpCollectorApi.kt:146)

### 这个接口负责什么

- 接收单个文件分片
- 校验偏移量
- 追加写入服务器暂存文件
- 更新该附件的已上传字节数

### 常见失败表现

- 上传百分比不增长
- 某个文件传到一半断掉
- 重新上传后仍然从同一位置失败

### 常见原因

- 请求被中断或网络不稳定
- 客户端偏移量和服务端状态不一致
- 分片 Base64 数据损坏
- 后端暂存文件写入异常

### 后端典型错误

- `404 NOT_FOUND`：`目标附件不存在`
- `400 BAD_REQUEST`：`分片编码不合法`
- 文件偏移不匹配或服务器写盘失败时，通常会在服务端日志中体现

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:73)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:141)

### 维护建议

- 先看是否所有失败都集中在某个 `fileId`
- 再看该 `recordId` 的分片是否持续更新
- 如果怀疑断点状态错误，再查 `GET /api/uploads/{recordId}`

---

## 4.4 上传完成确认

### 功能现象

- 所有图片和语音都传完了，但最后仍然提示上传失败
- 进度 100% 后没有变成“已上传”

### 对应 API

- `POST /api/uploads/{recordId}/complete`

### 调用方

- 安卓端：[HttpCollectorApi.kt](/home/sm6/project/android-app/app/src/main/java/com/shipyard/collector/data/remote/HttpCollectorApi.kt:87)

### 这个接口负责什么

- 确认所有文件分片已上传完成
- 将分片暂存文件转为最终文件
- 调用后端网关执行“写入平台”
- 最终把状态改成 `UPLOADED` 或 `FAILED`

### 常见失败表现

- 已经传完文件，但最后状态还是失败
- APP 看到“上传失败”而不是“已上传”

### 常见原因

- 仍有附件未完成
- 某个附件状态未标记 complete
- 后端网关写入失败
- 真氚云接入后，此处也会是平台写入失败的主要落点

### 后端典型错误

- `400 BAD_REQUEST`：`当前记录还没有任何已登记的附件`
- `409 CONFLICT`：`仍有附件未上传完成`
- 网关失败时，记录状态通常会变成 `FAILED`

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:83)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:184)

### 说明

当前代码里，真正决定“上传成功还是失败”的关键点就在这里。

---

## 4.5 上传状态查询

### 功能现象

- 需要确认一条记录现在到底是接收中、失败、还是成功
- 上传完成后想确认服务端最终状态

### 对应 API

- `GET /api/uploads/{recordId}`

### 调用方

- 安卓端：[HttpCollectorApi.kt](/home/sm6/project/android-app/app/src/main/java/com/shipyard/collector/data/remote/HttpCollectorApi.kt:174)

### 这个接口负责什么

- 返回该 `recordId` 的服务端状态
- 返回每个附件当前进度

### 常见用途

- 分片卡住时看哪一个文件没完成
- 上传完成后确认最终状态
- 定位“客户端显示失败，但服务端实际已成功”这类状态不一致问题

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:92)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:123)

---

## 4.6 继续上传

### 功能现象

- 用户点击“继续上传”
- 网络恢复后恢复上次任务

### 对应 API

- `POST /api/uploads/{recordId}/resume`

### 服务端作用

- 重新进入上传流程
- 若服务端记录是 `CANCELLED`，则恢复到可继续状态

### 常见失败表现

- 用户点击继续，但服务端状态没有恢复

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:101)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:214)

---

## 4.7 取消上传

### 功能现象

- 用户点击“取消批次”

### 对应 API

- `POST /api/uploads/{recordId}/cancel`

### 服务端作用

- 将服务端记录状态标记为 `CANCELLED`
- 保留本地缓存，允许后续再次上传

### 服务端代码入口

- 控制器：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:109)
- 服务：[UploadService.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/service/UploadService.java:229)

---

## 5. 管理后台功能与 API 对照表

## 5.1 管理员登录

### 对应 API

- `POST /api/auth/login`

### 特殊说明

- 后台登录后，前端还会额外判断返回结果里的 `admin`
- 即使登录成功，如果不是管理员，也会提示不能进入后台

### 前端代码

- [App.jsx](/home/sm6/project/admin-web/src/App.jsx:81)

---

## 5.2 后台首页初始化

### 对应 API

- `GET /api/me`
- `GET /api/admin/users`
- `GET /api/admin/forms`

### 功能现象

- 后台登录后加载用户列表和表单列表

### 常见失败表现

- 后台登录后白屏或提示报错
- 用户列表加载失败
- 表单列表为空

### 常见原因

- token 丢失或失效
- 当前账号不是管理员
- 后端 CORS 配置有问题

### 服务端代码入口

- 用户上下文：[ShipyardApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/ShipyardApiController.java:44)
- 管理员接口：[AdminApiController.java](/home/sm6/project/backend/src/main/java/com/shipyard/backend/api/AdminApiController.java:25)

---

## 5.3 新建账号

### 对应 API

- `POST /api/admin/users`

### 功能现象

- 后台创建手机号账号

### 常见失败表现

- 创建账号时报错
- 前端提示 400/403/409

### 排查方向

- 是否以管理员身份登录
- 手机号是否已存在
- 表单 ID 是否合法

---

## 5.4 重置密码

### 对应 API

- `POST /api/admin/users/{userId}/reset-password`

### 典型用途

- 工人忘记密码后由管理员重置

---

## 5.5 启用/禁用账号

### 对应 API

- `POST /api/admin/users/{userId}/status`

### 功能现象

- 后台切换账号启用状态

### 影响

- 被禁用的账号后续登录会失败
- 已有登录态也可能失效

---

## 5.6 修改权限

### 对应 API

- `POST /api/admin/users/{userId}/permissions`

### 功能现象

- 修改表单权限
- 修改是否允许上传
- 修改是否允许删除缓存

### 常见失败表现

- 修改后安卓端仍提示无权限

### 排查方向

- 后台是否修改成功
- 用户是否重新登录以刷新权限

---

## 6. 常见 HTTP 状态码含义

维护时最常见的状态码解释如下：

- `200`：请求成功
- `400`：请求参数或业务数据不合法
- `401`：未登录、token 无效、token 过期、账号不可用
- `403`：有登录态，但没有权限
- `404`：目标记录、附件、资源不存在
- `409`：状态冲突，例如记录已归属其他账号、附件未传完
- `500`：后端代码异常、存储异常、网关异常

---

## 7. 看到什么现象，优先查哪个 API

| 现象 | 优先检查的 API | 备注 |
|---|---|---|
| 安卓登录失败 | `POST /api/auth/login` | 先看账号、密码、地址、后端是否启动 |
| 后台登录失败 | `POST /api/auth/login` | 若非管理员，还会被前端拦住 |
| 后台登录后列表加载失败 | `GET /api/me` / `GET /api/admin/users` / `GET /api/admin/forms` | 管理员权限或 token 问题居多 |
| 点击上传立刻失败 | `POST /api/uploads/init` | 上传权限、表单权限、图片数量校验 |
| 上传进度卡在中途 | `POST /api/uploads/{recordId}/files/{fileId}/chunks` | 网络、偏移、文件写入 |
| 文件都传完但最终失败 | `POST /api/uploads/{recordId}/complete` | 最终确认或平台写入失败 |
| 继续上传无效 | `POST /api/uploads/{recordId}/resume` | 状态恢复问题 |
| 取消上传无效 | `POST /api/uploads/{recordId}/cancel` | 服务端状态未变更 |

---

## 8. 建议日志查看方式

## 8.1 后端日志

重点关注：

- 鉴权失败
- `ResponseStatusException`
- 上传初始化
- 分片写入
- 最终确认
- 网关写入结果
- 统一请求链路日志中的 `traceId / userId / recordId / fileId`

当前版本已补上统一请求日志，后端会为每个 API 请求打印：

- `traceId`
- `userId`
- `recordId`
- `fileId`
- `method / path / status / durationMs`

维护时建议优先搜索：

- `traceId=`
- `recordId=`
- `fileId=`
- `userId=`

建议搜索关键词：

- `登录态`
- `上传`
- `recordId`
- `fileId`
- `FORBIDDEN`
- `UNAUTHORIZED`
- `CONFLICT`
- `FAILED`

## 8.2 安卓日志

重点关注：

- `HttpCollectorApi`
- `UploadForegroundService`
- `Socket`
- `Exception`
- `com.shipyard.collector`

如果维护时是“安卓端显示失败，但不清楚哪个请求失败”，先从这几个关键词搜起。

当前版本已补上上传链路失败日志，安卓端在接口失败时会额外打印：

- 接口方法和路径，例如 `POST /uploads/init`
- `traceId`
- `recordId`
- `fileId`
- HTTP 状态码或异常信息

因此维护时，如果 APP 只提示“上传失败”，先看安卓日志里最近一条 `HttpCollectorApi` 的 `API failure`，基本就能直接定位是 `init`、`chunks`、`complete` 还是 `GET /uploads/{recordId}` 出错。

---

## 9. 标准排障顺序

建议维护时统一按下面顺序处理：

1. 先确认后端是否正常启动
2. 再确认接口地址是否正确
3. 再确认账号和 token 是否有效
4. 再看当前功能对应的是哪个 API
5. 对照状态码和报错文本判断问题层级
6. 如果是上传问题，按 `init -> chunks -> complete -> getUpload` 这个顺序查

补充建议：

- 先从安卓日志拿到失败请求路径和 `traceId`
- 再去后端日志按同一个 `traceId` 搜索整条链路
- 如果是分片问题，再结合 `recordId + fileId` 看是否总卡在同一个文件或同一个偏移阶段

---

## 10. 当前版本特别说明

当前版本有两个维护时需要特别记住的点：

1. 安卓端真正高频使用的核心接口其实不多，主要就是：
- `POST /api/auth/login`
- `POST /api/uploads/init`
- `POST /api/uploads/{recordId}/files/{fileId}/chunks`
- `POST /api/uploads/{recordId}/complete`
- `GET /api/uploads/{recordId}`

2. “上传成功”的最终判断点不是分片传完，而是：
- `POST /api/uploads/{recordId}/complete`

也就是说：

- 分片传完不代表成功
- 只有最终确认成功，记录才会变成 `UPLOADED`

---

## 11. 已落地补强与后续项

本轮已完成：

- 后端为每个请求打印 `traceId / userId / recordId / fileId`
- 安卓端失败日志补上接口方法和路径
- 上传链路补上统一 `traceId`

落地方式说明：

- 安卓上传链路会在请求头中带上 `X-Trace-Id`
- 同时附带 `X-Record-Id`
- 分片上传时还会附带 `X-File-Id`
- 后端会把这些信息统一写入请求完成日志
- 后端响应头也会回传 `X-Trace-Id`

当前仍建议后续继续补：

- 真氚云接入后，再补一版“平台回执排障手册”
