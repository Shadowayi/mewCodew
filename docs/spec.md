# MewCode Spec

## 背景

从零构建一个命令行 AI 助手 MewCode（仿 ClaudeCode 的轻量学习项目）。当前工作区为空项目，无任何既有代码。MewCode 定位为纯对话型 CLI，暂不含 tool use、文件操作、代码编辑等 agent 能力。

用户在终端启动后进入交互式 REPL，输入问题，MewCode 调用 LLM API 并将回复以流式逐字打印。支持多轮对话，模型能记住本次会话历史。

## 目标

- G1: 提供一个可在终端启动的 MewCode 可执行入口，进入交互式对话界面
- G2: 支持流式输出——回复逐字打印，而非等待全部生成后一次返回
- G3: 支持多轮对话，会话内历史完整保留，模型能看到此前对话
- G4: 通过统一 Provider 接口支持 Anthropic Claude 和 OpenAI 两种后端，配置切换
- G5: 支持 Claude extended thinking 的流式展示
- G6: 用 YAML 配置文件管理多个 LLM 供应商配置

## 功能需求

- F1: 启动与配置加载
  - 启动 MewCode 时从 `~/.mewcode/config.yaml` 读取 LLM 供应商配置
  - 若配置文件不存在，自动生成一个带占位符的模板配置文件并友好提示用户填写
  - 配置文件含多个 provider，通过 `--config <name>` 指定使用哪个；未指定时默认使用列表中的第一个

- F2: 交互式对话（REPL）
  - 启动后进入命令行为提示符（如 `> `），接收用户输入
  - 支持输入历史记录（上下方向键翻阅）
  - 支持命令：`/exit`（退出）、`/clear`（清空会话历史）、`/help`（列出命令）
  - 未知命令给出提示，不崩溃

- F3: 流式对话请求
  - 用户输入后，MewCode 按所选 provider 的协议构造请求，调用 LLM API
  - 回复通过 SSE 流式接收，**逐字打印**到终端，而非等全部生成完
  - 支持多轮：每次请求携带完整会话历史，模型能看到此前对话

- F4: 统一 Provider 接口
  - 抽象统一 Provider 接口，Anthropic 和 OpenAI 各自实现
  - 通过 `protocol` 字段（如 `anthropic` / `openai`）选择对应实现
  - 新后端只需新增实现即可接入，不改动上层逻辑

- F5: 双后端协议适配
  - OpenAI 后端：messages 格式，SSE 事件流逐条解析增量内容
  - Anthropic 后端：messages 格式，SSE 事件流解析 `content_block_delta` 增量
  - 两个后端共用同一份会话历史抽象，仅序列化格式不同

- F6: Extended Thinking 支持
  - 仅 Anthropic 后端支持：当配置 `thinking: true` 时开启 Claude extended thinking
  - 思考过程以灰色/斜体样式流式打印，与正式回复之间有视觉分隔
  - 思考内容不在终端展示，仅随后续请求回传给 API（Anthropic 硬性要求）；正式回复进入会话历史

- F7: 错误处理
  - API 调用失败（网络错误、HTTP 4xx/5xx、鉴权失败）时打印清晰错误信息，会话不崩溃
  - 可继续下一轮对话

## 非功能需求

- N1: 流式体验——首个字符应在收到后尽快打印，无明显延迟；打字输出应平滑，不发生大段卡顿后突然涌出
- N2: 可移植性——Java 11+ 跨平台运行（Linux / macOS / Windows）
- N3: 配置安全——api_key 仅存于本地配置文件，不写入日志、不回显到终端
- N4: 可维护性——Provider 层清晰解耦，新增后端不改动对话/界面层代码
- N5: 交互健壮性——会话中任何错误（网络、协议解析、未知配置）都不应导致进程崩溃，给出可理解的提示后能继续使用
- N6: 退出干净——`/exit` 或 EOF（Ctrl-D）可正常退出，不残留挂起的后台线程

## 不做的事

- 不实现 tool use、函数调用、文件操作、代码编辑等 agent 能力
- 不实现会话持久化（/save、/load 或跨启动恢复历史）
- 不实现滑动窗口裁剪历史（会话内全保留，超出模型上下文长度的处理后续再议）
- 不实现流式输出的中断/停止（发送后需等本轮结束）
- 不实现 OpenAI 的 extended thinking 或 reasoning 展示（仅 Claude）
- 不做任何 GUI 或 Web 界面
- 不实现多账号轮询、代理等高级网络能力
- 不写测试的自动化测试框架部分——但每个功能用可运行的手工验证

## 验收标准

- AC1（对应 F1）：首次启动时自动生成 `~/.mewcode/config.yaml` 模板文件，其中包含至少一个占位 provider，用户可编辑后正常启动
- AC2（对应 F1）：`mewcode --config <name>` 使用指定配置；不带参数时使用配置列表第一个，均能正常工作
- AC3（对应 F2）：在 REPL 中输入问题可得到回复；上下方向键可翻阅输入历史
- AC4（对应 F2）：`/exit` 退出进程；`/clear` 清空历史后模型不再记得此前对话（可验证：先问『我叫 Mew』，/clear 后再问『我叫什么』，回复应不含该信息）；`/help` 打印命令列表；未知命令给出提示且不崩溃
- AC5（对应 F3）：回复逐字流式打印，可观察到文字逐步出现而非一次性输出；停止打字后内容完整
- AC6（对应 F4）：配置文件将 `protocol` 改为 `openai` 并填入合法 OpenAI 配置后，MewCode 使用 OpenAI 后端正常工作
- AC7（对应 F4/F5）：同一套多轮对话在 OpenAI 和 Anthropic 两个后端下均能正确记住上下文
- AC8（对应 F6）：Anthropic 配置 `thinking: true` 时，回复先出现灰色思考文字，随后正式回复，两者之间有视觉分隔；`thinking: false` 时不出现思考文字；thinking 开启时多轮对话能正常进行（thinking 块随请求回传，不在终端展示）
- AC9（对应 F7）：填入错误 api_key 后启动对话，返回清晰的鉴权错误信息，进程不崩溃，可继续输入下一轮
- AC10（对应 N6）：`/exit` 与 Ctrl-D 均能干净退出，进程无异常堆栈、无残留进程
