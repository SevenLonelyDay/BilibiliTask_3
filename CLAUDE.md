# BilibiliTask 项目说明

## 📌 这是什么

[srcrs/BilibiliTask](https://github.com/srcrs/BilibiliTask) 的延续版本。
原项目已多年未维护，B 站接口陆续变更，本仓库负责把它修好并持续跑下去。

- **原始项目**: https://github.com/srcrs/BilibiliTask
- **原作者**: [srcrs](https://github.com/srcrs)

## 技术栈

| 项 | 版本 |
|---|---|
| Java | 17 |
| Gradle | 8.14.3（wrapper） |
| fastjson2 | 2.0.62 |
| httpclient | 4.5.14 |
| logback | 1.5.38 |
| snakeyaml | 2.6 |
| JUnit | 5.14.4 |
| shadow | com.gradleup.shadow 8.3.11 |
| lombok plugin | io.freefair.lombok 8.14.4 |

## 常用命令

```bash
./gradlew build         # 编译 + 单元测试（不需要 Cookie）
./gradlew test          # 只跑单元测试
./gradlew runMain       # 真正执行一次每日任务（需要 Cookie 环境变量）
./gradlew shadowJar     # 打成可独立运行的 fat jar
```

环境变量：`BILI_JCT`、`SESSDATA`、`DEDEUSERID` 为必填；
`LOG_LEVEL`、`BILI_TIMEOUT_MINUTES`、`BILI_UA` 可选。

## 代码结构

```
top.srcrs
├── BiliStart              程序入口：读 Cookie、读配置、跑任务、推送结果
├── Task                   所有任务的接口
├── domain
│   ├── Config             config.yml 映射（静态字段 + JavaBean 访问器，供 SnakeYAML 使用）
│   ├── UserData           账号运行时状态（单例）
│   └── VideoInfo          统一各来源的视频信息
├── task                   具体任务，每个类实现 Task
│   ├── bigvip/            大会员权益、B 币券
│   ├── daily/             观看分享、投币
│   ├── live/              直播签到、送礼物、银瓜子兑换
│   └── manga/             漫画签到
└── util
    ├── BiliApi            ★ 所有接口地址集中在这里，接口变更只改这一个文件
    ├── Request            HTTP 层：Cookie 组装、限速、重试、响应解析
    ├── WbiSignature       WBI 签名（有对照官方示例的单元测试）
    ├── BiliTicket         bili_ticket 生成
    ├── Account            拉 nav 接口刷新账号状态与 WBI 密钥
    ├── VideoSource        视频来源：热门 / 推荐 / 关注动态 / UP 主投稿
    ├── DailyReward        每日任务完成情况（字段名归一化 + 单次运行内缓存）
    ├── TaskRegistry       任务清单，顺序显式写死
    └── Send*/Notifier     推送渠道
```

## 维护要点

1. **接口坏了先看 `BiliApi`**。所有 URL 都在那里，带注释说明用途。
   注意 `src/main/resources/api.json` 是原项目留下的历史资料，代码并不读它，
   里面不少地址早已下线，**不要拿它当依据**。
2. **改 WBI 签名一定要跑 `WbiSignatureTest`**，它对照的是官方公开的示例值。
3. **任务执行顺序在 `TaskRegistry` 里显式定义**，不要改回按类名排序：
   `CollectVipGift` 必须排在 `BiCoinApply` 前面（先领券再花券），
   `BiLiveTask` 必须排在 `GiveGiftTask` 前面（签到礼物到账才能送出）。
4. **`Request` 不抛异常**，失败时返回 `code = -1` 的 JSON。读返回码用 `Request.code()`、
   读文案用 `Request.message()`，不要直接 `getIntValue("code")`——漫画接口会返回字符串 code。
5. **新增请求要走 `Request`**，它统一处理限速、风控 Cookie 和 Referer。
6. **定时任务会被 GitHub 因仓库不活跃而停用**，`keepalive` 工作流负责重置这个计时器。

## 工作流

| 文件 | 作用 | 需要 Secrets |
|---|---|---|
| `.github/workflows/Bilibili.yml` | 每天执行一次每日任务 | 是 |
| `.github/workflows/build.yml` | PR / 分支推送时编译 + 测试 | 否 |
| `.github/workflows/keepalive.yml` | 每月空提交，防止定时任务被停用 | 否 |
