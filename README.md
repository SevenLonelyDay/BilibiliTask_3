<div align="center">
<h1 align="center">Bilibili 助手（复活版）</h1>

<img src="https://img.shields.io/badge/Fork-srcrs/BilibiliTask-blue">
<img src="https://img.shields.io/badge/Java-17-orange">
<img src="https://img.shields.io/badge/Gradle-8.14-02303A">
<img src="https://img.shields.io/badge/Status-Revived-success">
</div>

# 简介

哔哩哔哩（`B` 站）自动完成每日任务：观看、分享、投币、直播签到、银瓜子兑换硬币、
送出即将过期的礼物、漫画 `App` 签到、大会员领取 `B` 币券等。每天获得 `65` 点经验，
助你快速升级到 `Lv6`。

本仓库是 [srcrs/BilibiliTask](https://github.com/srcrs/BilibiliTask) 的延续版本。
原项目已多年未维护，B 站接口在这几年里改了不少，这里把它重新修好了。

# ⚠️ 从旧版本升级过来的话，先看这里

**如果你的仓库已经很久没跑了，光更新代码是不够的**——GitHub 规定「仓库连续 60 天没有活动，
定时任务会被自动停用」，需要手动打开一次：

1. 进入仓库的 `Actions` 页面；
2. 左侧选中 `Bilibili` 工作流，如果顶部提示 *This scheduled workflow is disabled because
   there hasn't been activity in this repository for at least 60 days*，点 **Enable workflow**；
3. 点 `Run workflow` 手动跑一次，确认日志正常。

仓库里新增了 `keepalive` 工作流，每月推一个空提交把这个 60 天的计时器重置掉，
以后就不会再被悄悄停用了。不想要自动提交的话，删掉 `.github/workflows/keepalive.yml` 即可。

# 这次修好了什么

## 接口层面（项目"失效"的直接原因）

| 失效的地方 | 现状 |
|---|---|
| `x/web-interface/dynamic/region` 分区视频 | 接口已下线，改用综合热门 + 首页推荐 |
| `api.vc.bilibili.com/dynamic_svr/...` 关注动态 | 接口已下线，改用 `x/polymer/web-dynamic/v1/feed/all` |
| `x/space/arc/search` UP 主投稿 | 已要求 WBI 签名，改用 `x/space/wbi/arc/search` |
| `gift/v2/live/bag_send` 送礼物 | 接口已下线，改用 `xlive/revenue/v1/gift/sendBag` |
| `relation/v1/AppWeb/getRecommendList` 推荐直播间 | 接口已下线，改用分区直播间列表 |
| `pay/v1/Exchange/silver2coin` 银瓜子兑换 | 改用 `xlive/revenue/v1/wallet/silver2coin`，旧地址保留兜底 |
| `sc.ftqq.com` 旧版 Server 酱 | 服务已下线，统一走 Turbo 版 `sctapi.ftqq.com` |
| `pushplus.hxtrip.com` | 域名已停用，改为 `www.pushplus.plus` |

## 逻辑层面（跑得起来但结果是错的）

+ **WBI 签名漏掉了参数值的 URL 编码**，凡是含空格或中文的参数一律签名失败；
  同时清掉了一个并不存在的 `w_ks` 参数。现在算法与官方示例逐位对齐，
  并有单元测试守着（`WbiSignatureTest`）。
+ **`bili_ticket` 从来没生成成功过**：参数被塞进了 POST 表单体，而接口只认查询串，
  服务端一直回 `-400 empty 'ts' field`。这个 Cookie 缺失会明显提高被风控的概率。
+ **"今日已投币"永远读成 0**：接口从旧的 `home/reward` 换成 `x/member/web/exp/reward` 之后，
  字段名由 `coins_av` 变成了 `coins`，代码却没跟着改，于是每次运行都会把 5 个币重新投一遍。
+ **限速逻辑会自己把自己饿死**：每分钟计数器不会归零，跑到后面每个请求都要干等一分钟，
  再撞上写死的 2 分钟超时，任务经常没跑完就被强杀。现在换成真正的滑动窗口，超时也可配置。
+ **投币请求里塞了 `eab_x`、`ramval`、`ga` 等接口并不认识的参数**，还套着三层重试，
  每次重试前又拉一遍视频详情——请求量翻好几倍，反而更容易触发风控。现在只发官方要求的参数。
+ **`hideString` 在用户名短于 3 个字时会死循环**（负数长度撞上 `length-- != 0`）。
+ **每日任务固定取视频列表的第 6 条**，接口少返回几条就直接数组越界。
+ **任务执行顺序靠类名字典序**，原注释里写着"在 Linux 中并不是字典排序我就很迷茫"。
  顺序本来就是业务需求（先领到 B 币券才谈得上花掉它），现在显式写死并有测试保证。
+ **大会员权益写死每月 1 号才领**，错过就得等下个月。现在改为先查 `x/vip/privilege/my`，
  哪些没领就领哪些，任何一天跑都能补上。

## 工程层面

+ Java 11 → **17**；Gradle 8.5 → **8.14.3**；`johnrengelman/shadow` → 维护中的 `com.gradleup.shadow`
+ 依赖升级：fastjson2 `2.0.62`、logback `1.5.38`、snakeyaml `2.6`、JUnit `5.14.4`
+ 新增 **26 个单元测试**，覆盖 WBI 签名、字段归一化、配置解析、响应解析等纯逻辑部分
+ 新增 `Build` 工作流：PR 和分支推送时只做编译 + 测试，**不需要任何 Secrets，也不会碰你的账号**
+ 新增 `keepalive` 工作流，防止定时任务被 GitHub 自动停用
+ 请求补上了 `buvid3` / `buvid4` / `bili_ticket` 三个风控相关的 Cookie
+ UA 不再随机拼出并不存在的 Chrome 版本号，改用真实 UA，可用 `BILI_UA` 覆盖
+ Dockerfile 从已停止维护的 JDK 8 镜像改为多阶段构建 + `eclipse-temurin:17`
+ 清掉了误提交进仓库的 1500 行 CI 日志和 `.idea/` 目录

> 说明：接口相关的改动依据的是 B 站 web 端的公开行为和社区维护的
> [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) 文档。
> 编译和单元测试是绿的，但**能否真正跑通取决于你自己账号的实际情况**，
> 建议首次部署后先手动触发一次，看一遍日志再交给定时任务。

重要提示：如果收到了 `B` 站的账号安全通知，可以考虑将 `Actions` 禁用一段时间，
观望一阵再继续使用，具体步骤参考原项目的 [Issues](https://github.com/srcrs/BilibiliTask/issues/78)。

# 功能

* [x] 自动获取经验（观看、分享、投币、点赞）
* [x] 直播辅助（直播签到，自动送出即将过期的礼物）
* [x] 自动兑换银瓜子为硬币
* [x] 自动领取大会员每月权益（`B` 币券、权益礼包，不再限定 1 号）
* [x] 月底自动用 B 币券给自己充电（每月 `28` 号）
* [x] 月底自动用 B 币券兑换金瓜子（每月 `28` 号）
* [x] 漫画辅助脚本（漫画 `APP` 签到）
* [x] 支持功能自定义（投币数量、银瓜子兑换开关等）
* [x] 账户失效提醒
* [x] 多种方式推送运行结果（微信、钉钉、Telegram）
* [x] 支持 Docker 自行部署

# 目录

- [简介](#简介)
- [从旧版本升级过来的话，先看这里](#️-从旧版本升级过来的话先看这里)
- [这次修好了什么](#这次修好了什么)
- [功能](#功能)
- [Github Actions 部署方法](#github-actions-部署方法)
- [本地运行](#本地运行)
- [Docker 部署方法](#docker-部署方法)
- [进阶使用](#进阶使用)
- [如何拉取最新代码](#如何拉取最新代码)
- [更新日志](#更新日志)
- [致谢与参考](#致谢与参考)

关于日志中的 ✔ 和 ❌ 说明

符号 | 说明
-|-
✔ | 本次程序运行，成功地执行了代码并完成了任务（例如分享视频，今日未分享过，程序请求了分享接口并成功）。
❌ | 两种情况：1. 程序尝试去完成任务，但中途遇到了失败；2. 检测到此类任务已经完成（例如今日已分享过），无需再做。可以理解为跳过或遇到错误。

# Github Actions 部署方法

## 1. fork 本项目

## 2. 准备需要的参数

本项目成功运行需要三个参数：`SESSDATA`、`bili_jct`、`DedeUserID`

- 打开 `B` 站首页（任意页面都行）--> 按下 `F12` --> `Application` --> `Cookies` --> `https://www.bilibili.com`
- 找到所需参数对应的数据，找不到可能是账号没有登录。

![](img/获取Cookie.png)

## 3. 将获取到的参数填到 Secrets

`Settings` --> `Secrets and variables` --> `Actions` --> `New repository secret`

Name | Value
-|-
BILI_JCT | xxxxx
DEDEUSERID | xxxxx
SESSDATA | xxxxx

![](img/添加Secrets.png)

## 4. 开启 actions

默认 `actions` 处于禁止状态，在 `Actions` 选项中开启，把那个绿色的长按钮点一下。
如果看到左侧工作流上有黄色 `!` 号，还需继续开启。

![](img/开启actions.gif)

## 5. 手动跑一次确认

在 `Actions` --> `Bilibili` --> `Run workflow` 手动触发一次，看日志是否正常。
往 `main` 分支推代码同样会触发一次运行。

![](img/运行结果.gif)

之后每天北京时间 `09:27` 会自动执行。

# 本地运行

需要 JDK 17 及以上：

```bash
# 设置 Cookie
export BILI_JCT=xxx
export SESSDATA=xxx
export DEDEUSERID=xxx

# 跑一次
./gradlew runMain

# 只编译 + 跑单元测试（不需要 Cookie，不会碰你的账号）
./gradlew build

# 打成一个可直接运行的 jar
./gradlew shadowJar
java -jar build/libs/BilibiliTask-*-all.jar
```

可选的环境变量：

变量 | 说明
-|-
`LOG_LEVEL` | 日志级别，排查问题时设成 `DEBUG`，默认 `INFO`
`BILI_TIMEOUT_MINUTES` | 整体超时时间（分钟），默认 `10`
`BILI_UA` | 自定义 UserAgent，默认用内置的真实 UA

# Docker 部署方法

原来的 `timmyovo/bilibilitask` 镜像已多年未更新，请自行构建：

```bash
docker build -t bilibilitask .

docker run --rm \
  -e BILI_JCT=自行填写 \
  -e DEDEUSERID=自行填写 \
  -e SESSDATA=自行填写 \
  bilibilitask
```

容器跑一次就退出，定时执行交给宿主机的 `cron`、`systemd timer` 或 Kubernetes `CronJob`。

# 进阶使用

## 1. 配置文件说明

配置文件的位置在 `src/main/resources/config.yml`。

重要提示！！！

程序检测到礼物有效期还剩 `1` 天将会自动随机送出，部分朋友包裹里可能会有贵重礼物，
可以手动关闭该功能：把 `gift` 设置为 `false`。

配置项 | 说明
-|-
coin | 每日投币的数量 [0,5]
gift | 是否送出即将过期的礼物 [true,false]
s2c | 是否将银瓜子兑换成硬币 [true,false]
autoBiCoin | 月底如何使用 B 币券 [{0,自己另有用途},{1,给自己充电},{2,兑换成金瓜子}]
platform | 漫画签到使用的设备标识 [android,ios]
upList | 优先给这些 up 主投币，填写其 uid
manga | 是否自动进行漫画签到 [true,false]
upLive | 优先把即将过期的礼物送给此 up 的直播间，填写其 uid
selectLike | 投币时是否顺带点赞，默认不点赞 [0,1]

```yml
#每天需要投币的数量 [0,5]。
coin: 5
#送出即将过期礼物 [true,false]
gift: true
#银瓜子兑换为硬币 [true,false]
s2c: true
#月底自动使用B币卷 [{0,自己会使用},{1,给自己充电},{2,兑换成金瓜子}]
autoBiCoin: 1
#用户设备的标识 [android,ios]
platform: android
#自定义优先给这些 up 的视频投币 , 以yml数组的形式 , 填写其 uid (mid)
upList:
  - 477137547
  - 14602398
#进行漫画签到任务 [true,false]
manga: true
#优先送出即将过期礼物给此up的直播间,填写其 uid
upLive: 477137547
#对于进行投币的视频选择是否点赞 , 默认不点赞 [0,1]
selectLike: 0
```

## 2. 推送运行结果到微信

### Server 酱

> 旧版 Server 酱 `sc.ftqq.com` 已经下线，现在只支持 Turbo 版。
> 如果你的 Secrets 里还留着 `SCKEY`，程序会当作 `SENDKEY` 使用并给出提示，
> 建议直接改名。

官网：<https://sct.ftqq.com/>

+ 按官网教程用微信扫码登录，获得 `SENDKEY` 并填入 `Secrets`。

Name | Value
-|-
SENDKEY | xxxxx

![](img/server酱推送的结果.jpg)

### pushplus

官网：<https://www.pushplus.plus>

+ 进入官网点击"一对一推送"，用微信扫码关注，即可看到 `token`。

Name | Value
-|-
PUSHPLUSTK | xxxxx

## 3. 推送运行结果到钉钉

1. 在钉钉创建一个群聊（可以拉两个人建群，再把他们踢出去）。
2. 获取钉钉自定义机器人的 `Webhook`，填写到 `Secrets`。

Name | Value
-|-
DINGTALK | https://oapi.dingtalk.com/robot/send?access_token=xxxxx

![](img/获取钉钉Webhook.gif)

## 4. 使用 Telegram bot 推送到 Telegram 群组

1. 创建 telegram bot 并获取 token，可参考[文档](https://core.telegram.org/bots#how-do-i-create-a-bot)；
2. 将机器人加入群组并获取群组 ID，参考 [Stackoverflow 问答](https://stackoverflow.com/a/32572159)；
3. 把 token 和群组 ID 添加到 `Secrets`。

Name               | Value
-------------------|------
TELEGRAM_BOT_TOKEN | xxxxx
TELEGRAM_CHAT_ID   | xxxxx

![](img/TgBot运行结果.jpg)

## 5. 自定义程序运行时间

在 `.github/workflows/Bilibili.yml` 中修改 `cron` 表达式。注意 `cron` 用的是 UTC 时间，
换算到北京时间要往后推 8 个小时，例如 UTC `27 1 * * *` 就是北京时间 `09:27`。

![](img/自定义程序运行时间.png)

另外，GitHub 的定时任务在整点前后排队最严重，把分钟数设成一个不那么整的数字更容易准点触发。

# 如何拉取最新代码

## 方法一

在 `github` 安装 `pull`，会自动帮你检测上游仓库并更新代码：<https://github.com/apps/pull>

由于仓库里带有配置文件 `config.yml`，有可能会覆盖你自定义的内容，需要注意。

## 方法二

```sh
# 查看是否已有上游仓库
git remote -v

# 添加上游仓库
git remote add upstream https://github.com/chuiba/BilibiliTask_3

# 拉取上游 main 分支的更新
git pull upstream main

# 推送到你自己的仓库
git push origin main
```

# 更新日志

## 2026-07-26 复活版 (v2.0.0)

+ 替换 6 个已经下线的 B 站接口，详见[这次修好了什么](#这次修好了什么)
+ 修好 WBI 签名的 URL 编码、`bili_ticket` 生成、每日任务字段名、限速死锁等一批逻辑错误
+ 升级到 Java 17 / Gradle 8.14.3，依赖全部更新
+ 新增 26 个单元测试与独立的 `Build` 工作流
+ 新增 `keepalive` 工作流，避免定时任务被 GitHub 自动停用
+ 补上 `buvid3` / `buvid4` / `bili_ticket` 等风控相关 Cookie
+ 重写 Dockerfile，清理误提交的 CI 日志与 IDE 配置

## 2025-01-21 兼容性修复版

+ Java 8 → 11，Gradle 6.7.1 → 8.5
+ fastjson 1.2.80 → fastjson2，修复已知 CVE
+ 移除 `latest.release` 依赖，改用固定版本号
+ GitHub Actions 工作流现代化

---

## 原项目更新历史

**以下为原作者 [srcrs](https://github.com/srcrs) 的更新记录：**

## 2020-02-06

+ 避免投币给单一`up`主

+ 更新`b`币卷充电接口

+ 修复投币计算(投币数为负数)

+ 修复近30天未投币出现的bug

## 2020-12-17

+ 增加`server`酱测试号版推送，感谢[sh4wnzec](https://github.com/sh4wnzec)

+ 增加`Telegram bot`推送，感谢[qiwihui](https://github.com/qiwihui)

## 2020-12-07

+ 发布1.0.8版本

+ 修复用户无动态获取视频信息错误

+ 修复从动态列表中获取到自己投稿视频的错误

+ 增加一些自定义配置点赞

## 2020-11-28

+ 去除UA(貌似是没有影响的)

+ 设置API请求间的缓冲时间(5 秒钟)

## 2020-11-22

感谢[东酱](https://github.com/qq523407234)在此项目中做出的巨大贡献。

+ 增加`push+`推送方式

+ 将项目管理工具改为`gradle`

+ 优化代码结构，优化日志输出格式(高亮)

## 2020-11-17

+ 增加钉钉推送方式

+ 优化投币给视频的策略 , 自定义`up`主视频 > 当前用户动态列表中的视频投稿 > 随机视频列表

+ 投币后等待`1-2`秒钟 , 降低访问投币`API`的速度

+ 优化模拟观看视频 , 获取视频时常，随机上报视频观看进度

+ 优化日志输出，提示更加友好，更加贴近用户(如增加了还剩多少天升级的提示)

+ 增加账号失效提醒(发送到微信或者钉钉)

## 2020-11-05

+ 根据阿里巴巴代码规范优化代码

+ 增加用户标识配置项

## 2020-11-03

+ 将自动使用B币卷开关，更改为自动配置用途，可以选择不使用、充电、兑换金瓜子。

+ 增加B币卷兑换金瓜子功能

## 2020-10-22

+ 增加用server酱推送运行结果到微信功能

## 2020-10-19

+ 增加年度大会员每月`1`号领取`B`币卷

+ 月底自动用`B`币卷给自己充电

+ 在配置项中添加是否月底用`B`币卷给自己充电开关，默认开启

## 2020-10-17

+ 优化日志显示

+ 增加账户失效提醒

## 2020-10-13

+ 重构代码，功能不变

+ 采用反射实现自动加载`task`包功能任务代码。

+ 加入配置文件，用户可自定义一些配置

## 2020-10-08

+ 增加自动送出即将过期的礼物

+ 增加漫画`APP`签到

+ 增加一些`api`

## 2020-10-07

+ 增添银瓜子自动兑换硬币功能

## 2020-10-06

+ 增添B站直播签到

+ 继续增添`API`

## 2020-10-05

+ 完成了自动获取经验功能

每日登录、每日观看视频、每日投币、每日分享

+ 完善对接`api`接口

# 致谢与参考

## 🙏 特别感谢

+ **[srcrs/BilibiliTask](https://github.com/srcrs/BilibiliTask)** —— 原作者 [srcrs](https://github.com/srcrs)。
  本项目是该仓库的延续，所有核心功能与设计归原作者所有。
+ **[SocialSisterYi/bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect)** —— 接口与签名算法参考
+ **[happy888888/BiliExp](https://github.com/happy888888/BiliExp)** —— API 参考

## ⚖️ 声明

+ 本项目仅供学习交流，请勿用于任何商业用途
+ 使用本项目造成的任何后果由使用者自行承担
+ 如有任何问题，建议优先参考原项目
