# MewCode Tasks

> 依据：docs/spec.md + docs/plan.md（均已批准）
> 语言：Java 17 · Maven · 单模块

## 文件清单

| 操作 | 文件 | 职责 |
|------|------|------|
| 新建 | `pom.xml` | Maven 工程，Java 17，依赖 JLine3/SnakeYAML/Jackson |
| 新建 | `src/main/java/dev/mewcode/Main.java` | 参数解析、装配、启动 |
| 新建 | `src/main/java/dev/mewcode/config/ProviderConfig.java` | 单供应商配置 record |
| 新建 | `src/main/java/dev/mewcode/config/AppConfig.java` | 整体配置 record |
| 新建 | `src/main/java/dev/mewcode/config/ConfigLoader.java` | 加载/模板生成/选择 |
| 新建 | `src/main/resources/config-template.yaml` | 首次启动模板 |
| 新建 | `src/main/java/dev/mewcode/chat/ChatMessage.java` | 统一消息 record |
| 新建 | `src/main/java/dev/mewcode/chat/ChatRequest.java` | 请求 record |
| 新建 | `src/main/java/dev/mewcode/chat/StreamHandler.java` | 流式回调接口 |
| 新建 | `src/main/java/dev/mewcode/provider/ChatProvider.java` | 统一 Provider 接口 |
| 新建 | `src/main/java/dev/mewcode/provider/ProviderFactory.java` | 按 protocol 创建 Provider |
| 新建 | `src/main/java/dev/mewcode/sse/SseClient.java` | 通用 SSE 客户端 |
| 新建 | `src/main/java/dev/mewcode/provider/OpenAiProvider.java` | OpenAI 协议实现 |
| 新建 | `src/main/java/dev/mewcode/provider/AnthropicProvider.java` | Anthropic 协议实现 |
| 新建 | `src/main/java/dev/mewcode/chat/ChatController.java` | 历史管理与流式编排 |
| 新建 | `src/main/java/dev/mewcode/tui/OutputPrinter.java` | ANSI 样式渲染 |
| 新建 | `src/main/java/dev/mewcode/tui/Repl.java` | JLine3 交互循环 + 命令分派 |
| 新建 | `src/main/java/dev/mewcode/verify/*.java`（临时） | 手工验证辅助类，T12 移除 |

## 执行顺序

```
T1 → T2 → T3 → T4 → T5 → T6 ─→ T7
                            └→ T8
                  └──────→ T9 → T10 → T11 → T12
```

T7 与 T8 在 T6 之后可并行；T9 在 T5 之后即可开始。

---

## T1: Maven 工程骨架

**文件：** `pom.xml`、`src/main/java/dev/mewcode/Main.java`（占位）
**依赖：** 无
**步骤：**
1. 创建 `pom.xml`：
   - `<properties><maven.compiler.release>17</maven.compiler.release></properties>`
   - 依赖：`org.jline:jline:3.26.3`、`org.yaml:snakeyaml:2.2`、`com.fasterxml.jackson.core:jackson-databind:2.17.1`
   - 插件：`maven-shade-plugin`（mainClass `dev.mewcode.Main`）、`exec-maven-plugin`、`maven-compiler-plugin`（release 17）
   - `<finalName>mewcode</finalName>`，shade 后产出 `target/mewcode.jar`
2. 创建包目录结构 `config/ chat/ provider/ sse/ tui/ verify/`
3. `Main.java` 仅含 `public static void main(String[] args) { System.out.println("MewCode"); }` 占位

**验证：** `mvn -q compile` 成功，无错误

## T2: 配置模型

**文件：** `config/ProviderConfig.java`、`config/AppConfig.java`
**依赖：** T1
**步骤：**
1. `ProviderConfig`：`record ProviderConfig(String name, String protocol, String model, String baseUrl, String apiKey, boolean thinking)`，提供 `thinking` 默认 false 的便捷构造（`withThinkingDefault` 或重载构造）
2. `AppConfig`：`record AppConfig(List<ProviderConfig> providers)`
3. 均放在 `dev.mewcode.config` 包，保持包路径下唯一 `public` 类型（Java 文件与 record 名一致）

**验证：** `mvn -q compile` 成功

## T3: ConfigLoader 与配置模板

**文件：** `config/ConfigLoader.java`、`resources/config-template.yaml`
**依赖：** T2
**步骤：**
1. `ConfigLoader` 提供：
   - `AppConfig load(Path file)`：SnakeYAML 解析，属性命名策略将 `base_url` 映射到 `baseUrl`（`new Constructor` + `PropertyUtils` 的 `setAllowMissingProperties`，或用 `LoaderOptions` 设置 `NameAwarePropertyUtils`）
   - `Path defaultConfigPath()`：返回 `~/.mewcode/config.yaml`（取 `user.home`）
   - `Path generateTemplate(Path file)`：目录不存在则创建；把资源 `config-template.yaml` 复制到目标路径（用 `Files.copy` 从 classpath 读取）
   - `loadOrDefault()`：`load(defaultConfigPath())`；文件不存在时先 `generateTemplate` 再加载
   - `ProviderConfig resolve(AppConfig config, String name)`：name 为空取 `providers.get(0)`，否则按 name 匹配；未匹配抛 `IllegalArgumentException` 带可读消息
2. `config-template.yaml` 模板内容：
```yaml
providers:
  - name: anthropic-default
    protocol: anthropic
    model: claude-sonnet-4-20250514
    base_url: https://api.anthropic.com
    api_key: YOUR_ANTHROPIC_API_KEY
    thinking: false
  - name: openai-default
    protocol: openai
    model: gpt-4o-mini
    base_url: https://api.openai.com/v1
    api_key: YOUR_OPENAI_API_KEY
```
3. 新建 `verify/VerifyConfig.java`：临时目录下 `generateTemplate` → 断言文件存在且含占位 key → `load(临时文件)` 断言解析出 2 个 provider、`baseUrl` 正确映射（`base_url`→`baseUrl`）→ `resolve(name)` 断言正确选择与默认取第一个

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifyConfig` 输出 `OK: template+load+resolve` 且断言全部通过

## T4: 消息与请求模型

**文件：** `chat/ChatMessage.java`、`chat/ChatRequest.java`、`chat/StreamHandler.java`
**依赖：** T1
**步骤：**
1. `ChatMessage`：`record ChatMessage(String role, String content, String thinking)`，加静态工厂 `user(String)`（thinking=null）、`assistant(String content, String thinking)`
2. `ChatRequest`：`record ChatRequest(String model, String baseUrl, String apiKey, List<ChatMessage> messages, boolean thinking)`
3. `StreamHandler`：
```java
public interface StreamHandler {
    void onThinkingDelta(String delta);
    void onTextDelta(String delta);
    void onComplete();
    void onError(Throwable t);
}
```

**验证：** `mvn -q compile` 成功

## T5: Provider 接口与工厂

**文件：** `provider/ChatProvider.java`、`provider/ProviderFactory.java`
**依赖：** T3、T4
**步骤：**
1. `ChatProvider`：
```java
public interface ChatProvider {
    String protocol();
    void streamChat(ChatRequest request, StreamHandler handler);
}
```
2. `ProviderFactory`：`static ChatProvider create(ProviderConfig config)`，按 `config.protocol()` 匹配——`anthropic` 返回 `AnthropicProvider`、`openai` 返回 `OpenAiProvider`（两类可先留空实现，T7/T8 填充）、否则抛 `IllegalArgumentException("unknown protocol: " + protocol)`

**验证：** `mvn -q compile` 成功

## T6: SseClient

**文件：** `sse/SseClient.java`
**依赖：** T5
**步骤：**
1. `SseRequest` record：`(String uri, Map<String,String> headers, byte[] body)`（可放同文件或独立文件）
2. `SseClient.stream(SseRequest req, Consumer<String> dataConsumer)`：
   - `HttpClient.newBuilder().connectTimeout(...).build()`
   - `HttpRequest` POST，`BodyPublishers.ofByteArray(body)`，设置 headers，`HttpResponse.BodyHandlers.ofInputStream()`
   - `response.body()` 用 `new BufferedReader(new InputStreamReader(..., StandardCharsets.UTF_8))` 逐行读
   - 行以 `data:` 开头：取 `substring(5).trim()`，空串跳过，否则传给 `dataConsumer`（含 `[DONE]`，由 Provider 判断）
   - 读到 `EOF` 后方法返回；HTTP 状态 >=400 时抛 `IOException`（带状态码与响应体摘要）
3. 新建 `verify/MockSseServer.java`：用 `com.sun.net.httpserver.HttpServer` 起本地随机端口服务，收到 POST 后按预设字符串列表逐条以 `data: <payload>\n\n` 写出，记录收到的请求体
4. 新建 `verify/VerifySse.java`：启动 mock，构造 `SseRequest` 发起，断言收到的 payload 数量与内容等于预设

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifySse` 输出 `OK: got 3 payloads`，内容匹配

## T7: OpenAiProvider

**文件：** `provider/OpenAiProvider.java`
**依赖：** T6
**步骤：**
1. `protocol()` 返回 `"openai"`
2. 端点：`baseUrl + "/chat/completions"`；headers：`Authorization: Bearer <apiKey>`、`Content-Type: application/json`
3. 请求体（Jackson `ObjectMapper`）：
```json
{"model":"<model>","messages":[{"role":"user","content":"..."}],"stream":true}
```
   `messages` 由 `ChatRequest.messages()` 映射，忽略 `thinking` 字段
4. 解析 SSE：每个 `data:` 载荷：
   - 载荷为 `[DONE]` → `handler.onComplete()`，结束
   - 否则 JSON 解析，取 `choices[0].delta.content`（字符串，非 null 时 `handler.onTextDelta(content)`）；`delta.reasoning_content` 忽略
   - `choices[0].finish_reason` 非 null → `handler.onComplete()`（兜底）
5. 异常：IO/解析异常包装后 `handler.onError(throwable)`；HTTP 4xx/5xx 从异常消息中提取状态码与 body 摘要传回
6. 新建 `verify/VerifyOpenAi.java`：MockSseServer 预设 OpenAI 格式（两个 content chunk + `[DONE]`），断言 onTextDelta 拼接结果与预期一致、onComplete 恰好触发一次、onError 未触发

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifyOpenAi` 输出 `OK: delta text matches, complete once, no error`

## T8: AnthropicProvider

**文件：** `provider/AnthropicProvider.java`
**依赖：** T6
**步骤：**
1. `protocol()` 返回 `"anthropic"`
2. 端点：`baseUrl + "/v1/messages"`；headers：`x-api-key: <apiKey>`、`anthropic-version: 2023-06-01`、`Content-Type: application/json`；`thinking` 开启时加 `anthropic-beta: thinking-2024-12-11`
3. 请求体：
```json
{"model":"<model>","max_tokens":8192,"stream":true,
 "thinking":{"type":"enabled","budget_tokens":4096},
 "messages":[{"role":"user","content":"..."}]}
```
   - 仅 `thinking` 开启时含 `thinking` 对象
   - assistant 消息序列化：有 `thinking` 字段时 content 为 `[{"type":"thinking","thinking":"..."},{"type":"text","text":"..."}]`，否则为纯字符串
4. 解析 SSE，按 `event:` 区分（载荷是 JSON）：
   - `content_block_start`：block 的 `type == "thinking"` 时进入 thinking 态
   - `content_block_delta`：`delta.type == "thinking_delta"` → `onThinkingDelta(delta.thinking)`；`delta.type == "text_delta"` → `onTextDelta(delta.text)`
   - `message_stop` → `handler.onComplete()`
   - 其余事件忽略
5. 异常处理同 T7 第 5 步
6. 新建 `verify/VerifyAnthropic.java`：MockSseServer 预设「thinking 事件 + text 事件 + message_stop」，断言 onThinkingDelta 与 onTextDelta 的顺序与内容、onComplete 恰好一次

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifyAnthropic` 输出 `OK: thinking then text, complete once`

## T9: ChatController

**文件：** `chat/ChatController.java`
**依赖：** T5、T4
**步骤：**
1. 构造：`ChatController(ProviderConfig config, ChatProvider provider)`
2. `void send(String userInput)`：
   - 把 `ChatMessage.user(userInput)` 追加进 `history`
   - 构造 `ChatRequest(config.model(), config.baseUrl(), config.apiKey(), new ArrayList<>(history), config.thinking())`
   - 调 `provider.streamChat(request, handler)`；handler 内部累积 `StringBuilder text` 与 `StringBuilder thinking`
   - `onTextDelta`：text.append + 转发给界面回调（见下）
   - `onThinkingDelta`：thinking.append + 转发
   - `onComplete`：`assistant` 消息（`ChatMessage.assistant(text.toString(), thinking.isEmpty()? null : thinking.toString())`）追加进 history，转发完成事件
   - `onError`：打印友好错误（`出错：<可读原因>`），历史不追加
3. `void clear()`：清空 `history`
4. `List<ChatMessage> history()`：只读返回
5. 界面回调注入：构造参数加 `Consumer<ChatController.Event>` 或直接让 controller 持有 `StreamHandler` 接口的实现由 Repl 传入——采用 `send(String input, StreamHandler view)`：view 负责打印，controller 负责累积与历史
6. 新建 `verify/VerifyController.java`：`FakeProvider`（实现 `ChatProvider`，不开网络，`streamChat` 直接回调 onThinkingDelta/onTextDelta/onComplete 各若干次）；断言 send 后 history 含 user+assistant、assistant.thinking 非空、clear 后为空、FakeProvider 抛错时 history 不追加且不抛异常

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifyController` 输出 `OK: history, thinking, clear, error-path` 全部通过

## T10: OutputPrinter 与 Repl

**文件：** `tui/OutputPrinter.java`、`tui/Repl.java`
**依赖：** T9
**步骤：**
1. `OutputPrinter`：
   - `printThinking(String s)`：不换行，`\u001b[90m`（灰）+ `\u001b[3m`（斜体）+ s + `\u001b[0m` 包裹
   - `printText(String s)`：不换行普通输出
   - `printLine(String s)`：换行
   - `printInfo(String s)` / `printError(String s)`：带前缀（如 `[i]` / `[!]`），Error 用红色 `\u001b[31m`
   - 思考转正式回复的分隔：`printInfo("---")` 由调用方触发
2. `Repl`：
   - 构造：`Repl(ChatController controller, OutputPrinter printer)`，Controller 的 `send` 回调打印实现
   - JLine3：`TerminalBuilder.builder().build()`，`LineReaderBuilder.builder().terminal(t).build()`，`reader.setVariable(LineReader.HISTORY_FILE, ...)` 启用输入历史，提示符 `"> "`
   - 包内可见方法 `Dispatch dispatch(String line)` 返回枚举 `EXIT / CLEAR / HELP / UNKNOWN / CHAT`，规则：`/exit`→EXIT、`/clear`→CLEAR、`/help`→HELP、以 `/` 开头且非以上→UNKNOWN、否则 CHAT
   - `run()` 循环：读行（EOF 返回 null 时退出）→ dispatch → EXIT 跳出；CLEAR 调 `controller.clear()` 并提示；HELP 打印命令表；UNKNOWN 提示可用命令；CHAT 调 `controller.send(line, view)`
   - `view`：onThinkingDelta→`printer.printThinking`，onTextDelta→`printer.printText`，onComplete→`printer.printLine("")` 换行 + 打印分隔，onError→`printer.printError`
3. 新建 `verify/VerifyRepl.java`：构造 Repl（用 T9 的 FakeProvider + 无终端模式），直接调用 `dispatch` 对 `/exit` `/clear` `/help` `/foo` `hello` 断言枚举正确；不启动真实终端

**验证：** `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.verify.VerifyRepl` 输出 `OK: dispatch 5/5`

## T11: Main 端到端装配

**文件：** `Main.java`（填充）
**依赖：** T10、T7、T8
**步骤：**
1. 解析参数：遍历 args，`--config <name>` 记录 name；未知参数打印用法并退出码 1
2. 装配：
   - `ConfigLoader.loadOrDefault()` → `AppConfig`
   - `ConfigLoader.resolve(config, name)` → `ProviderConfig`
   - `ProviderFactory.create(providerConfig)` → `ChatProvider`
   - `new ChatController(providerConfig, provider)` → controller
   - `new Repl(controller, new OutputPrinter())` → `run()`
3. 顶层 try/catch：`IllegalArgumentException`（未知 protocol/未知 config name）与 IO 异常打印 `[!]` 错误并退出码 1

**验证（手工，mock 服务器）：**
1. 准备 `mock-config.yaml`（protocol: openai，base_url 指向 `http://localhost:<port>`，api_key 随意）
2. 终端运行 `mvn -q compile exec:java -Dexec.mainClass=dev.mewcode.Main -Dexec.args="--config mock"`（或打 jar 后 `java -jar target/mewcode.jar`）
3. 输入一句话 → 观察到回复逐字流式打印（来自 mock 的 chunk）→ 输入 `/help` → 输入 `/exit`，进程干净退出

## T12: 清理临时验证类

**文件：** `src/main/java/dev/mewcode/verify/*.java`（删除，需用户确认）
**依赖：** T11
**步骤：**
1. 列出 verify 包下所有临时类（MockSseServer、VerifyConfig/Sse/OpenAi/Anthropic/Controller/Repl）
2. 向用户确认后删除该包
3. `mvn -q clean package` 重新构建，确认产物只含生产代码

**验证：** `mvn -q package` 成功，`target/mewcode.jar` 可运行且不含 verify 类
