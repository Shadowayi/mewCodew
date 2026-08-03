# MewCode Plan

> 依据：docs/spec.md（已批准，AC8 已按方案 ii 修订）

## 架构概览

采用「入口 → 配置 → 交互 → 会话 → 协议」五层单向依赖结构，上层只依赖下层接口：

```
Main（入口/参数解析）
  └→ ConfigLoader（配置加载/模板生成）
  └→ Repl（JLine3 交互循环）
       └→ ChatController（会话历史管理 + 流式编排）
            └→ ChatProvider（统一接口）
                 ├→ AnthropicProvider
                 └→ OpenAiProvider
                      └→ SseClient（通用 SSE 客户端，基于 JDK HttpClient）
```

新增后端只实现 `ChatProvider` 一个接口，Repl 与 ChatController 完全不感知协议差异。

## 核心数据结构与接口

### ProviderConfig（单个供应商配置，对应 YAML 六字段）
```java
public record ProviderConfig(
    String name,      // 供应商标识名
    String protocol,  // anthropic | openai
    String model,     // 模型名
    String baseUrl,   // 请求地址（YAML 键 base_url）
    String apiKey,    // 认证
    boolean thinking  // 是否启用扩展思考（YAML 可选，默认 false）
) {}
```

### AppConfig（整体配置）
```java
public record AppConfig(List<ProviderConfig> providers) {}
```

### ChatMessage（统一会话历史抽象）
```java
public record ChatMessage(String role, String content, String thinking) {}
// role: user | assistant；thinking 可空，仅 assistant 消息携带，
// 供 Anthropic 把 thinking 块随请求回传；OpenAI 忽略该字段。
// 便捷构造：ChatMessage.user(content) / ChatMessage.assistant(content, thinking)
```

### ChatRequest（一次流式调用的请求）
```java
public record ChatRequest(
    String model, String baseUrl, String apiKey,
    List<ChatMessage> messages, boolean thinking
) {}
```

### ChatProvider（统一 Provider 接口）
```java
public interface ChatProvider {
    String protocol();                        // 返回 "anthropic" / "openai"
    void streamChat(ChatRequest request, StreamHandler handler);
}
```

### StreamHandler（流式回调，Repl 实现）
```java
public interface StreamHandler {
    void onThinkingDelta(String delta);   // 思考增量（灰色斜体打印）
    void onTextDelta(String delta);       // 正式回复增量（正常打印）
    void onComplete();                    // 本轮结束（触发历史追加）
    void onError(Throwable t);            // 出错（打印后继续）
}
```

### SseClient（SSE 客户端）
```java
public record SseRequest(String uri, Map<String,String> headers, byte[] body) {}
public final class SseClient {
    public void stream(SseRequest req, Consumer<String> dataConsumer)
            throws IOException, InterruptedException;
}
```
`dataConsumer` 每次收到一个 SSE `data:` 载荷的完整字符串（已去 `data:` 前缀），由各 Provider 自行 JSON 解析。

## 模块设计

### 1. Main（入口）
**职责：** 解析命令行参数 `--config <name>`，加载配置，选择 provider，启动 Repl
**对外接口：** `main(String[])`
**依赖：** ConfigLoader、Repl

### 2. ConfigLoader（配置）
**职责：** 读取 `~/.mewcode/config.yaml`；文件不存在时从资源模板生成；按 name 选择 provider（无 `--config` 时取第一个）
**对外接口：** `AppConfig load()`, `ProviderConfig resolve(String name)`
**依赖：** 无

### 3. Repl（TUI 层）
**职责：** 用 JLine3 提供提示符、输入历史、命令分派（`/exit` `/clear` `/help`）；将流式回调渲染到终端（thinking 用灰色斜体）
**对外接口：** `void run()`
**依赖：** ChatController

### 4. ChatController（会话层）
**职责：** 维护 `List<ChatMessage>` 会话历史；每轮把历史 + 新输入交给 Provider 流式调用；流式过程中自行累积 text 与 thinking 增量，同时转发给 Repl 打印；`onComplete` 时把完整回复（含 thinking）追加进历史；错误时兜底不崩溃
**对外接口：** `void send(String userInput, StreamHandler view)`（`view` 负责打印增量，controller 内部再包一层 handler 累积并与 view 串联）
**依赖：** ChatProvider、StreamHandler

### 5. AnthropicProvider / OpenAiProvider（协议层）
**职责：** 各自构造请求体（messages 格式），用 SseClient 发起请求，解析各自 SSE 事件，转换为统一的 `StreamHandler` 回调
**对外接口：** 实现 `ChatProvider.streamChat(ChatRequest, StreamHandler)`
**依赖：** SseClient

### 6. SseClient（基础层）
**职责：** 基于 `java.net.http.HttpClient` 发起流式 POST，把响应体逐行解析出 SSE `data:` 载荷，交给消费者
**对外接口：** `void stream(SseRequest, Consumer<String>)`
**依赖：** 无

## 模块交互（数据流）

一轮对话的调用链：

```
Repl 读到用户输入（非命令）
  → ChatController.send(input)
      → 构造 ChatRequest(历史 + input, 当前配置)
      → selectedProvider.streamChat(req, handler)
          → AnthropicProvider / OpenAiProvider 按协议构造 HTTP 请求体
          → SseClient.stream() 发起流式 POST
              → 逐行解析 SSE，把 data: 载荷交给 Provider
          → Provider 解析事件：
              OpenAI:      chunks[i].choices[0].delta.{content, reasoning_content}
              Anthropic:   content_block_delta 的 text / thinking_delta
          → handler.onThinkingDelta(...) / onTextDelta(...)  [逐段回传]
      → ChatController 把增量转发给 Repl 打印
  → onComplete()：ChatController 把完整回复追加进会话历史
  → 回到 Repl 等待下一轮
```

`onError` 路径：任何异常（网络/HTTP 4xx 5xx/解析失败）由 ChatController 统一转为用户可读消息打印，历史不追加，REPL 继续运行。

## 文件组织

```
mewcode/
├── pom.xml                          — Maven 工程，Java 17
└── src/main/
    ├── java/dev/mewcode/
    │   ├── Main.java                — 参数解析、启动
    │   ├── config/
    │   │   ├── AppConfig.java       — 整体配置 record
    │   │   ├── ProviderConfig.java  — 单供应商 record
    │   │   └── ConfigLoader.java    — 加载/模板生成/选择
    │   ├── chat/
    │   │   ├── ChatMessage.java     — 统一消息 record
    │   │   ├── ChatRequest.java     — 请求 record
    │   │   ├── ChatController.java  — 历史管理与流式编排
    │   │   └── StreamHandler.java   — 流式回调接口
    │   ├── provider/
    │   │   ├── ChatProvider.java    — 统一 Provider 接口
    │   │   ├── ProviderFactory.java — 按 protocol 创建 Provider 实例
    │   │   ├── AnthropicProvider.java
    │   │   └── OpenAiProvider.java
    │   ├── sse/
    │   │   └── SseClient.java       — 通用 SSE 客户端
    │   └── tui/
    │       ├── Repl.java            — JLine3 交互循环 + 命令分派
    │       └── OutputPrinter.java   — ANSI 样式渲染（thinking 灰色斜体）
    └── resources/
        └── config-template.yaml     — 首次启动生成的模板
```

## 技术决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Java 版本 | 17 LTS | 自带 HttpClient 流式、record、switch 表达式；跨平台 |
| 构建工具 | Maven | 单模块标准工程，简单直观 |
| JSON 解析 | Jackson | Java 生态事实标准，支持流式解析 |
| YAML 解析 | SnakeYAML | Java 标准 YAML 库 |
| REPL | JLine3 | Java 终端事实标准，输入历史/ANSI 开箱即用 |
| SSE | 手写解析（JDK HttpClient + BufferedReader） | 两个后端事件均为 `data:` JSON，解析 ~50 行，符合方案 A |
| `base_url` 键映射 | SnakeYAML 属性命名策略 | YAML 用 `base_url`，record 字段 `baseUrl`，通过命名策略映射 |
| 错误处理 | ChatController 统一捕获 | 所有 Provider 异常收敛到一处转用户提示，满足 N5 健壮性 |
| thinking 回传 | 方案 ii：thinking 入历史但不打印 | Anthropic API 硬性要求多轮携带 thinking 块；AC8 已修订为「不在终端展示」 |

## Spec 需求覆盖对照

| Spec 需求 | 落地位置 |
|-----------|---------|
| F1 启动与配置加载 | ConfigLoader + Main + config-template.yaml |
| F2 交互式对话 | Repl（JLine3 + 命令分派） |
| F3 流式对话请求 | ChatController + SseClient + Provider |
| F4 统一 Provider 接口 | ChatProvider 接口 + ProviderFactory 按 protocol 创建 |
| F5 双后端协议适配 | AnthropicProvider / OpenAiProvider |
| F6 Extended Thinking | AnthropicProvider（thinking_delta）+ OutputPrinter（灰色斜体） |
| F7 错误处理 | ChatController.onError 统一兜底 |
