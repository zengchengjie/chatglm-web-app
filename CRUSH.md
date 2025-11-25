# ChatGLM Web App - 开发指南

## 构建命令

### 后端 (Java/Spring Boot)
```bash
# 构建和测试
cd backend && mvn clean install

# 运行应用
cd backend && mvn spring-boot:run

# 运行单个测试
cd backend && mvn test -Dtest=ChatControllerTest

# 生产环境打包
cd backend && mvn package -DskipTests

# 使用特定配置运行
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 前端 (Vue 3/Vite)
```bash
# 安装依赖
cd frontend && npm install

# 开发服务器
cd frontend && npm run dev

# 生产环境构建
cd frontend && npm run build

# 代码检查
cd frontend && npm run lint

# 预览生产构建
cd frontend && npm run preview
```

## 代码风格指南

### 后端 (Java)
- **包结构**: `com.chatglm.webapp.{layer}` (controller, service, model, config, util, exception)
- **命名规范**: 类名使用驼峰命名法，方法和变量使用小驼峰，常量使用全大写下划线
- **依赖注入**: 使用 `@Autowired` 或构造器注入，推荐构造器注入
- **异常处理**: 使用继承 `RuntimeException` 的自定义异常，通过 `@ControllerAdvice` 统一处理
- **日志记录**: 使用 SLF4J，INFO级别记录请求/响应详情，ERROR级别记录错误
- **API设计**: RESTful端点，返回 `ApiResponse<T>` 包装器，使用正确的HTTP状态码
- **安全认证**: JWT身份验证，使用 `@RateLimit` 注解进行速率限制
- **测试框架**: JUnit 5 + Mockito，使用 `@ExtendWith(MockitoExtension.class)`

### 前端 (Vue 3)
- **框架**: Vue 3 组合式API，使用 `<script setup>` 语法
- **组件命名**: 组件名使用帕斯卡命名法，模板中使用短横线命名法
- **状态管理**: Pinia存储，按功能组织 (chatStore, userStore)
- **路由配置**: Vue Router，使用meta字段进行身份验证守卫
- **样式库**: Element Plus UI库，作用域样式，CSS类使用短横线命名法
- **API调用**: Axios工具函数，集中在 `utils/api.js` 中
- **错误处理**: Try-catch配合Element Plus通知显示用户友好消息
- **导入顺序**: Vue库 → 第三方库 → 本地组件/工具

### 配置管理
- **后端配置**: `application.yml`中的YAML配置，环境特定的配置文件
- **前端配置**: Vite配置，`/api`路由代理，路径别名 `@` 指向 `src/`
- **安全配置**: 永不提交API密钥，使用环境变量

### 测试策略
- **单元测试**: 服务层业务逻辑，使用MockMvc测试控制器端点
- **集成测试**: 使用真实HTTP调用测试API端点
- **前端测试**: Vue Test Utils组件测试（如已实现）
- **覆盖率**: 关键业务逻辑保持>80%的测试覆盖率

### 企业级特性
- **监控系统**: Spring Boot Actuator + Prometheus指标
- **Redis使用**: 多用途数据存储
  - **速率限制**: 令牌桶算法，键为 `rate_limit:token:{key}`, `rate_limit:counter:{key}`
  - **聊天历史**: 基于会话的对话存储，键为 `chat:history:{userId}:{sessionId}` (7天TTL)
  - **异步处理**: 临时消息状态跟踪，键为 `chat_message:{messageId}` (24小时TTL)
  - **使用统计**: 用户消息计数器，键为 `chat:usage:{userId}`
- **消息队列**: RabbitMQ用于异步处理
- **身份验证**: JWT，24小时过期时间

### Git工作流
- **分支策略**: `main` 用于生产环境，`develop` 用于集成
- **提交规范**: 约定式提交格式 (`feat:`, `fix:`, `docs:`, 等)
- **代码审查**: 所有更改都需要代码审查，重点关注安全性和性能