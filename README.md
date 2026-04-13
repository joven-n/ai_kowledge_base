# AI知识库问答系统（RAG + Agent）

> 企业级AI应用，支持文档知识库构建、检索增强生成（RAG）、多轮对话、Agent工具调用。
> 可直接用于求职项目展示，涵盖大模型应用开发全链路。

---

## 项目架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 / API调用方                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTP Request
┌──────────────────────────▼──────────────────────────────────────┐
│                    Spring Boot 应用层                            │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────┐  ┌──────────┐  │
│  │ AiController│  │ChatController│  │KnowledgeC│  │SystemCtrl│  │
│  └──────┬──────┘  └──────┬──────┘  └─────┬────┘  └──────────┘  │
└─────────┼────────────────┼───────────────┼─────────────────────┘
          │                │               │
┌─────────▼────────────────▼───────────────▼─────────────────────┐
│                      Service 服务层                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────────┐   │
│  │AiService │  │RagService│  │Conversation│  │KnowledgeService│  │
│  │(LLM调用) │  │(RAG检索) │  │Service    │  │(文档管理)    │   │
│  └──────┬───┘  └─────┬────┘  └─────┬─────┘  └───────┬───────┘  │
└─────────┼────────────┼─────────────┼─────────────────┼─────────┘
          │            │             │                  │
┌─────────▼────────────▼─────────────┼─────────────────▼─────────┐
│                      核心组件层                                  │
│  ┌────────────┐  ┌─────────────┐   │   ┌───────────────────┐   │
│  │AgentExecutor│  │VectorStore  │   │   │  TextSplitter     │   │
│  │(ReAct引擎) │  │(向量数据库) │   │   │  (文本切分)       │   │
│  └──────┬─────┘  └──────┬──────┘   │   └───────────────────┘   │
│  ┌──────▼─────┐         │          │                           │
│  │AgentTool   │         │          │                           │
│  │- SystemInfo│         │          │                           │
│  │- Weather   │         │          │                           │
│  │- Database  │         │          │                           │
│  └────────────┘         │          │                           │
└─────────────────────────┼──────────┼───────────────────────────┘
                          │          │
        ┌─────────────────▼──┐    ┌──▼──────────────────┐
        │   向量存储           │    │   大模型API           │
        │  InMemoryVectorStore│    │  OpenAI / 兼容接口    │
        │  MilvusVectorStore  │    │  (文本生成/Embedding)│
        └────────────────────┘    └─────────────────────┘
```

---

## 技术栈

| 层次 | 技术选型 | 版本 |
|------|---------|------|
| 后端框架 | Spring Boot | 3.2.0 |
| 编程语言 | Java | 17 |
| AI调用 | OpenAI API / 兼容接口 | - |
| 向量数据库 | Milvus（生产）/ 内存向量库（开发） | 2.3.x |
| HTTP客户端 | OkHttp3 | 4.12 |
| PDF解析 | Apache PDFBox | 3.0.1 |
| JSON处理 | Fastjson2 | 2.0.43 |
| 缓存 | ConcurrentMapCache（内存）/ Redis（生产） | - |
| 监控 | Spring Actuator + AOP | - |
| 构建工具 | Maven | 3.x |

---

## 项目结构

```
ai-knowledge-base/
├── pom.xml
└── src/main/java/com/aiknowledge/
    ├── AiKnowledgeBaseApplication.java      # 启动类
    ├── agent/
    │   ├── AgentExecutor.java               # Agent执行引擎（ReAct模式）
    │   └── tool/
    │       ├── AgentTool.java               # 工具接口（Strategy模式）
    │       ├── SystemInfoTool.java          # 系统信息工具
    │       ├── WeatherTool.java             # 天气查询工具
    │       └── DatabaseQueryTool.java       # 数据库查询工具
    ├── common/
    │   ├── Result.java                      # 统一响应封装
    │   ├── BusinessException.java           # 业务异常
    │   └── GlobalExceptionHandler.java      # 全局异常处理
    ├── config/
    │   ├── AiProperties.java                # AI配置属性
    │   ├── OkHttpConfig.java                # HTTP客户端配置
    │   ├── ThreadPoolConfig.java            # 线程池配置
    │   ├── CacheConfig.java                 # 缓存配置
    │   ├── LoggingAspect.java               # AOP日志切面
    │   ├── SystemController.java            # 系统监控接口
    │   └── DemoController.java              # Demo接口（无需API Key）
    ├── controller/
    │   ├── AiController.java                # 基础问答接口
    │   ├── ChatController.java              # RAG/多轮对话/Agent接口
    │   └── KnowledgeController.java         # 知识库管理接口
    ├── model/
    │   ├── dto/
    │   │   ├── AskRequest.java              # 问答请求
    │   │   └── OpenAiDto.java               # OpenAI数据模型
    │   ├── entity/
    │   │   ├── KnowledgeDocument.java       # 知识文档实体
    │   │   ├── DocumentChunk.java           # 文档切片实体
    │   │   └── ConversationSession.java     # 对话会话实体
    │   └── vo/
    │       └── AskResponse.java             # 问答响应VO
    ├── rag/
    │   ├── TextSplitter.java                # 文本切分器
    │   ├── VectorStore.java                 # 向量存储接口
    │   ├── InMemoryVectorStore.java         # 内存向量存储（余弦相似度）
    │   └── MilvusVectorStore.java           # Milvus向量存储
    └── service/
        ├── AiService.java                   # AI调用接口
        ├── KnowledgeService.java            # 知识库服务接口
        ├── RagService.java                  # RAG服务接口
        ├── ConversationService.java         # 会话管理接口
        └── impl/
            ├── AiServiceImpl.java           # AI调用实现（OpenAI兼容）
            ├── KnowledgeServiceImpl.java    # 知识库服务实现
            ├── RagServiceImpl.java          # RAG服务实现
            └── ConversationServiceImpl.java # 会话管理实现
```

---

## 快速启动

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- （可选）Milvus 2.3+（不装则自动使用内存向量库）

### 2. 配置API Key

编辑 `src/main/resources/application.yml`：

```yaml
ai:
  api:
    base-url: https://api.openai.com/v1       # 或其他兼容接口URL
    api-key: sk-your-api-key-here              # 替换为你的API Key
    chat-model: gpt-3.5-turbo                  # 对话模型
    embedding-model: text-embedding-ada-002    # Embedding模型
```

**兼容接口配置示例（SiliconFlow / 通义 / 智谱）：**

```yaml
# SiliconFlow
ai:
  api:
    base-url: https://api.siliconflow.cn/v1
    api-key: sk-xxxx
    chat-model: deepseek-ai/DeepSeek-V2.5
    embedding-model: BAAI/bge-m3

# 通义千问
ai:
  api:
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    api-key: sk-xxxx
    chat-model: qwen-plus
    embedding-model: text-embedding-v3
```

### 3. 运行

```bash
# 克隆/进入项目
cd ai-knowledge-base

# 编译运行
mvn spring-boot:run

# 或打包后运行
mvn package -DskipTests
java -jar target/ai-knowledge-base-1.0.0.jar
```

服务启动在 `http://localhost:8080`

---

## API接口文档

### 基础问答

```bash
# STEP 1: 直接调用大模型问答
curl -X POST http://localhost:8080/ai/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"什么是RAG技术？"}'
```

### RAG知识库问答

```bash
# STEP 1: 先上传知识文档
curl -X POST http://localhost:8080/kb/upload \
  -F "file=@document.pdf" \
  -F "docName=技术文档"

# 或添加文本内容
curl -X POST http://localhost:8080/kb/text \
  -H "Content-Type: application/json" \
  -d '{
    "content": "RAG（检索增强生成）是一种AI技术，通过检索相关文档来增强LLM的回答准确性...",
    "source": "技术博客"
  }'

# STEP 2: RAG问答
curl -X POST http://localhost:8080/ai/rag \
  -H "Content-Type: application/json" \
  -d '{"question":"RAG技术有什么优势？"}'
```

### 多轮对话

```bash
# 第一轮（不传sessionId）
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"你好，我想了解RAG技术"}'

# 响应中会返回 sessionId，第二轮携带它
curl -X POST http://localhost:8080/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"刚才说的有什么应用场景？","sessionId":"abc-123"}'
```

### Agent工具调用

```bash
# 查询天气（Agent自动选择weather_query工具）
curl -X POST http://localhost:8080/ai/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"北京今天天气怎么样？"}'

# 查询产品库存（Agent自动选择database_query工具）
curl -X POST http://localhost:8080/ai/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"帮我查一下产品P001的库存状态"}'

# 查询系统时间
curl -X POST http://localhost:8080/ai/agent \
  -H "Content-Type: application/json" \
  -d '{"question":"现在几点了？"}'
```

### Demo接口（无需API Key）

```bash
# 测试文本切分
curl -X POST http://localhost:8080/demo/split \
  -H "Content-Type: application/json" \
  -d '{"text":"这是一段很长的技术文档..."}'

# 注入测试向量数据
curl -X POST http://localhost:8080/demo/vector/inject

# 查看系统架构
curl http://localhost:8080/demo/architecture

# 系统健康检查
curl http://localhost:8080/system/health

# 查看所有API
curl http://localhost:8080/system/apis
```

---

## 核心设计亮点（面试重点）

### 1. 分层架构 + 接口隔离
- Controller → Service → Repository 标准三层
- VectorStore 接口，生产/开发两套实现一键切换
- AgentTool 接口，新工具无需改主流程，直接注入即用

### 2. RAG Pipeline设计
- **文本切分**：段落优先 + 句子兜底 + Overlap保持连贯性
- **向量化**：支持批量Embedding，减少API调用
- **相似度过滤**：阈值过滤低质量文档，降低LLM幻觉
- **Prompt工程**：严格约束LLM只用检索结果回答

### 3. ReAct Agent引擎
- 工具注册机制：Spring依赖注入 + Map索引，O(1)查找
- ReAct循环：最多3轮工具调用，防止无限循环
- 容错设计：JSON解析失败自动降级为直接回答

### 4. 多轮对话管理
- 会话TTL自动过期（30分钟）
- 历史裁剪算法：保留System Prompt + 最近N轮，防止Token爆炸
- SessionId设计：客户端无状态，服务端维护上下文

### 5. 可观测性
- AOP自动注入TraceId（MDC），全链路日志追踪
- 慢请求自动告警（>5秒）
- Spring Actuator暴露健康检查端点

---

## 简历描述（直接复制使用）

### 项目名称
**AI知识库问答系统（RAG + Agent）** | Java / Spring Boot / OpenAI API

### 项目描述（1句话版）
基于 Spring Boot 构建的企业级 AI 问答平台，集成 RAG 检索增强生成、多轮对话管理和 Agent 工具调用能力。

### 项目经历描述（选3-5条最匹配的岗位需求）

**版本A（侧重AI应用开发）**
1. 设计并实现基于 RAG（检索增强生成）的知识库问答系统，支持 txt/pdf 文档上传、自动文本切分（Chunk）、Embedding 向量化入库，通过余弦相似度实现 TopK 语义检索，显著提升 LLM 回答准确率，减少幻觉
2. 基于 ReAct（Reasoning + Acting）框架实现 Agent 执行引擎，通过 Spring 依赖注入实现工具动态注册（Strategy 模式），支持天气查询、数据库查询等多种工具，最大 3 轮迭代推理实现闭环决策
3. 实现多轮对话上下文管理，设计 Session TTL 过期机制和历史消息裁剪算法（保留最近 N 轮），在保持对话连贯性的同时有效控制 Token 消耗
4. 封装 AiService 统一 AI 调用门面，基于 OkHttp3 实现 OpenAI 兼容接口调用，支持 OpenAI / 通义千问 / 智谱 / SiliconFlow 等多种大模型一键切换
5. 设计 VectorStore 接口抽象向量存储层，实现内存版（余弦相似度，用于开发测试）和 Milvus 版（HNSW索引，用于生产）两套实现，满足不同规模场景需求

**版本B（侧重Java后端工程）**
1. 基于 Spring Boot 3.x + Java 17 构建 RESTful API 服务，采用 Controller/Service/Repository 标准分层架构，运用面向接口编程保证各模块可扩展性
2. 实现 AOP 日志切面，通过 MDC 为每个请求自动注入 TraceId 实现链路追踪，记录接口耗时，慢请求（>5秒）自动触发告警日志
3. 设计统一异常处理机制（GlobalExceptionHandler），覆盖业务异常、参数校验异常、系统异常三个层次，配合统一 Result 响应封装，规范化 API 输出格式
4. 使用 ThreadPoolTaskExecutor 配置异步任务线程池（核心10线程，最大50线程，队列200），支持文档处理和 AI 调用的高并发场景
5. 集成 Spring Actuator 提供 /health、/metrics 等监控端点，结合自定义 /system/health 接口暴露 JVM 内存、线程数等运行时指标

---

## 技术难点与解决方案（面试时的深度回答素材）

### Q1: 如何解决RAG中LLM幻觉问题？
**方案：** 三层防护
1. **相似度阈值过滤**：只向LLM提供相似度 >0.7 的文档，排除噪声
2. **Prompt约束**：在System Prompt中明确要求"只基于参考资料回答，没有相关信息要说没有"
3. **来源标注**：给每个检索结果标注来源和相似度分数，让LLM知道证据强度

### Q2: 如何防止多轮对话Token超限？
**方案：** 分级管理
1. **历史裁剪**：`trimHistory(maxTurns)` 只保留 System Prompt + 最近10轮
2. **System Prompt复用**：System Prompt不参与裁剪，始终保留
3. **升级方向**：可加入 `tiktoken` 精确计算Token数，按Token数而非轮数裁剪

### Q3: Agent如何保证不无限循环？
**方案：** `MAX_ITERATIONS = 3` 硬上限 + JSON解析容错
- 超过上限强制用最后的消息历史让LLM生成最终答案
- LLM响应非JSON格式时自动降级为直接回答

### Q4: 为什么用接口而不是直接实现？
**VectorStore接口的价值：**
- 开发环境用 `InMemoryVectorStore`，零依赖启动
- 生产环境用 `MilvusVectorStore`，亿级向量支持
- `@ConditionalOnProperty` 按配置自动切换，代码零修改

### Q5: 如何支持多种大模型？
**只需修改3个配置项：**
```yaml
ai.api.base-url: https://api.siliconflow.cn/v1
ai.api.api-key: sk-xxxx
ai.api.chat-model: deepseek-ai/DeepSeek-V2.5
```
AiServiceImpl 调用的是标准 OpenAI Chat Completions 格式，所有兼容接口均可直接接入。
