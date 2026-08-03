# MewCode Checklist

> 依据：docs/spec.md（AC1-AC10）+ docs/plan.md
> 每一项通过运行代码或观察行为验证，聚焦系统行为。

## 实现完整性

- [ ] 首次启动自动生成 `~/.mewcode/config.yaml` 模板，含 anthropic/openai 两个占位 provider，含 `name/protocol/model/base_url/api_key/thinking` 六字段（验证：备份并移除配置文件后启动 `java -jar target/mewcode.jar`，观察生成的文件内容）
- [ ] 配置解析正确：YAML 的 `base_url` 映射为 `baseUrl`，`thinking` 缺省为 false（验证：运行 `VerifyConfig`，断言全部通过）
- [ ] `--config <name>` 使用指定配置；不带参数时使用列表第一个（验证：临时目录两份配置分别用 `--config a` / `--config b` / 无参数启动，观察所选 provider 生效）
- [ ] REPL 输入问题得到回复，上下方向键可翻阅输入历史（验证：真实终端键入两轮不同输入后按 ↑ 键，出现上一条）
- [ ] `/exit` 退出进程；`/help` 打印命令表；未知命令（如 `/foo`）给出提示且不崩溃（验证：逐条输入观察）
- [ ] `/clear` 清空上下文：先问「我叫 Mew」，`/clear` 后再问「我叫什么」，回复不再含 Mew（验证：mock 服务器打印收到 messages 数，clear 后第二问 messages 只含该轮）
- [ ] 回复逐字流式打印，文字逐步出现而非一次性输出，结束后内容完整（验证：肉眼观察 + mock 延迟 chunk 时可见递增输出）

## 集成

- [ ] `ProviderFactory.create` 按 `protocol` 返回正确实现，未知 protocol 抛可读错误（验证：`VerifyConfig` 或临时配置 protocol=foo 启动，观察 `[!] unknown protocol`）
- [ ] `SseClient` 被 OpenAI 与 Anthropic 两个 provider 复用（验证：`VerifyOpenAi` 与 `VerifyAnthropic` 均通过，且实现代码中两 provider 调同一 `SseClient.stream`）
- [ ] `ChatController` 与 Repl 的 view 回调串联：增量打印、完成换行、错误红色提示（验证：`VerifyController` + 端到端场景）
- [ ] 多轮历史正确累积并随请求发出（验证：mock 服务器第二问收到的 messages 包含第一轮 user+assistant 两条）
- [ ] Anthropic thinking 开启时，后续请求回传上一轮 thinking 块（验证：`VerifyAnthropic` 断言请求体 messages 中 assistant 消息含 `thinking` 类型块）
- [ ] thinking 开启时回复先灰色斜体思考、分隔、再正式回复；关闭时无思考区（验证：mock 分别返回/不返回 thinking 事件，观察终端样式）

## 编译与测试

- [ ] `mvn clean package` 无错误，产出 `target/mewcode.jar`（验证：运行命令）
- [ ] 开发期临时验证类全部通过：`VerifyConfig` / `VerifySse` / `VerifyOpenAi` / `VerifyAnthropic` / `VerifyController` / `VerifyRepl`（验证：逐一运行 `mvn exec:java`，均输出 OK）
- [ ] lint：项目无 lint 配置，此项 N/A（验证：pom.xml 无 checkstyle/spotbugs 插件）

## 端到端场景

- [ ] 场景 1（openai 协议 + 多轮）：mock 服务器 + openai 配置启动 → 提问收到流式回复 → 再问，mock 收到含上轮的完整 messages → `/clear` 后问，messages 只含本轮 → `/exit` 干净退出（验证：真实终端走完整流程）
- [ ] 场景 2（错误处理）：mock 返回 401 → 启动提问，出现红色 `[!]` 鉴权错误，进程不崩溃，可继续输入下一轮（验证：观察终端）
- [ ] 场景 3（anthropic + thinking）：mock 返回 thinking 事件序列 → 灰色斜体思考 → 分隔线 → 正式回复，`/exit` 退出无异常堆栈（验证：观察终端样式与输出）
