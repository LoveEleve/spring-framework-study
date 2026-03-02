# 🔬 Spring MVC 源码调试实验手册

> 配套文档系列的**动手实验指南**。每个实验对应一篇源码分析文档，通过实际请求 + 断点调试来验证文档中的结论。

---

## 快速开始

### 启动项目

运行 `MvcApplication.main()` 即可（推荐 SCI 模式）。启动后控制台会打印 Filter 初始化日志：

```
[LoggingFilter] ✅ init() called — Filter initialized
[AuthFilter] ✅ init() called — Filter initialized
```

### 验证启动

```bash
curl http://localhost:8080/hello
# 预期输出: Hello Spring MVC Source Code!
```

---

## 实验一：Filter vs Interceptor 执行顺序（对应文档 ⑧）

### 🎯 目标
观察 Filter 和 Interceptor 的执行顺序差异，理解 "洋葱模型" vs "正向/逆向遍历"。

### 实验 1.1：正常请求 — 观察完整执行链路

```bash
curl http://localhost:8080/demo/param?name=test&age=18
```

**观察控制台输出顺序**：

```
[LoggingFilter] ▶ PRE-PROCESSING          ← Filter 1 进入
  [AuthFilter] ▶ PRE                       ← Filter 2 进入
    [LoggingInterceptor] preHandle()        ← Interceptor 1 进入
      [AuthInterceptor] preHandle()          ← Interceptor 2 进入
        → Controller method executes          ← 业务代码
      [AuthInterceptor] postHandle()          ← Interceptor 2 出（逆序）
    [LoggingInterceptor] postHandle()        ← Interceptor 1 出（逆序）
      [AuthInterceptor] afterCompletion()     ← Interceptor 2 完成（逆序）
    [LoggingInterceptor] afterCompletion()   ← Interceptor 1 完成（逆序）
  [AuthFilter] ◀ POST                       ← Filter 2 出（递归回栈）
[LoggingFilter] ◀ POST-PROCESSING          ← Filter 1 出（递归回栈）
```

**断点建议**：
1. `ApplicationFilterChain#internalDoFilter()` — 观察 `pos++` 如何选择下一个 Filter
2. `HandlerExecutionChain#applyPreHandle()` — 观察 `interceptorIndex` 递增
3. `HandlerExecutionChain#applyPostHandle()` — 观察逆序遍历

### 实验 1.2：Filter 短路 — AuthFilter 拒绝请求

```bash
curl -H "X-Auth: reject" http://localhost:8080/demo/param?name=test
```

**预期**：
- AuthFilter **不调用** `chain.doFilter()`
- DispatcherServlet **不会被调用**
- Interceptor **全部不执行**
- 返回 401 Unauthorized

**控制台只有**：
```
[LoggingFilter] ▶ PRE-PROCESSING
  [AuthFilter] ⛔ BLOCKED! Request short-circuited by Filter.
[LoggingFilter] ◀ POST-PROCESSING
```

**关键点**：Filter 短路后，LoggingFilter 的 `chain.doFilter()` 之后的代码仍然执行（因为它在递归调用栈中）。

### 实验 1.3：Interceptor 短路 — AuthInterceptor 拒绝请求

```bash
curl -H "X-Intercept: reject" http://localhost:8080/demo/param?name=test
```

**预期**：
- AuthInterceptor 的 `preHandle()` 返回 `false`
- Controller **不执行**
- AuthInterceptor 的 `postHandle()` 和 `afterCompletion()` **不执行**
- 但 LoggingInterceptor 的 `afterCompletion()` **仍然执行**（清理机制！）

**控制台**：
```
[LoggingFilter] ▶ PRE-PROCESSING
  [AuthFilter] ▶ PRE
    [LoggingInterceptor] preHandle() ✅ returns true
      [AuthInterceptor] preHandle() ⛔ returns FALSE!
    [LoggingInterceptor] afterCompletion() ← 仍然执行！这是关键区别
  [AuthFilter] ◀ POST
[LoggingFilter] ◀ POST-PROCESSING
```

**断点建议**：
- `HandlerExecutionChain#applyPreHandle()` L157 — 看 `interceptorIndex` 停在哪里
- `HandlerExecutionChain#triggerAfterCompletion()` — 看它只清理 `[0..interceptorIndex]`

### 实验 1.4：异常时的行为差异

```bash
curl http://localhost:8080/demo/ex/nullpointer
```

**观察**：
- Interceptor 的 `postHandle()` **不执行**（因为 Controller 抛了异常）
- Interceptor 的 `afterCompletion()` **仍然执行**（参数 `ex` 不为 null）
- Filter 的 post-processing **仍然执行**（递归调用栈的特性）

---

## 实验二：参数解析全家桶（对应文档 ⑤）

### 实验 2.1：@RequestParam — 基本参数解析

```bash
curl "http://localhost:8080/demo/param?name=Spring&age=18"
```

**断点链**：
```
DispatcherServlet#doDispatch()
  → HandlerAdapter#handle()
    → RequestMappingHandlerAdapter#handleInternal()
      → InvocableHandlerMethod#invokeForRequest()
        → InvocableHandlerMethod#getMethodArgumentValues()
          → RequestParamMethodArgumentResolver#resolveArgument()
            → AbstractNamedValueMethodArgumentResolver#resolveArgument()
              → TypeConverterDelegate#convertIfNecessary()  ← "18" → Integer
```

### 实验 2.2：@PathVariable — 路径变量解析

```bash
curl http://localhost:8080/demo/path/42
```

**断点**：`PathVariableMethodArgumentResolver#resolveName()`
**观察**：URI template 变量是在 `RequestMappingInfoHandlerMapping#handleMatch()` 中提前解析好的

### 实验 2.3：@RequestHeader — 请求头解析

```bash
curl -H "X-Token: my-secret-token" http://localhost:8080/demo/header
```

**断点**：`RequestHeaderMethodArgumentResolver#resolveName()`

### 实验 2.4：@RequestBody — JSON 反序列化

```bash
curl -X POST http://localhost:8080/demo/body \
  -H "Content-Type: application/json" \
  -d '{"name":"Spring","age":18}'
```

**断点链**：
```
RequestResponseBodyMethodProcessor#resolveArgument()
  → AbstractMessageConverterMethodArgumentResolver#readWithMessageConverters()
    → Content-Type 匹配：遍历 MessageConverter 列表
      → MappingJackson2HttpMessageConverter#canRead()  ← 匹配 application/json
        → MappingJackson2HttpMessageConverter#readInternal()
          → ObjectMapper.readValue()  ← Jackson 反序列化
```

### 实验 2.5：@ModelAttribute — 表单数据绑定

```bash
curl "http://localhost:8080/demo/model?name=Spring&age=18"
```

**断点链**：
```
ModelAttributeMethodProcessor#resolveArgument()
  → constructAttribute() → 创建空 UserDTO 对象
  → WebDataBinder#bind()
    → DataBinder#doBind() → applyPropertyValues()
      → BeanWrapperImpl#setPropertyValue("name", "Spring")
      → BeanWrapperImpl#setPropertyValue("age", "18")  ← 触发类型转换
```

---

## 实验三：数据绑定与类型转换（对应文档 ⑥）

### 实验 3.1：String → Date 自定义转换

```bash
curl "http://localhost:8080/demo/date?date=2024-01-15"
```

**断点**：`CustomDateEditor#setAsText()` — 由 `@InitBinder` 注册的自定义编辑器

### 实验 3.2：String → Enum 转换

```bash
curl "http://localhost:8080/demo/enum?status=ACTIVE"
```

**断点**：`TypeConverterDelegate#convertIfNecessary()` — 观察 PropertyEditor 查找过程

### 实验 3.3：@InitBinder 安全防护 — disallowedFields

```bash
# 注意：即使传了 role=admin，返回的 role 也是 null！
curl -X POST "http://localhost:8080/demo/bindSafe?name=test&age=18&role=admin"
```

**预期返回**：
```json
{
  "role": null,
  "name": "test",
  "role_is_null": true,
  "explanation": "'role' is in disallowedFields, so it's not bound even if provided",
  "age": 18
}
```

**断点**：`DataBinder#isAllowed()` — 观察 `disallowedFields` 如何阻止 `role` 字段绑定

---

## 实验四：异常处理机制（对应文档 ⑦）

### 实验 4.1：@ExceptionHandler 处理 NullPointerException

```bash
curl http://localhost:8080/demo/ex/nullpointer
```

**断点链**：
```
DispatcherServlet#doDispatch()  →  try { ha.handle() } catch (Exception ex)
  → processDispatchResult(request, response, mappedHandler, null, ex)
    → processHandlerException()
      → 遍历 handlerExceptionResolvers:
        → ExceptionHandlerExceptionResolver#doResolveHandlerMethodException()
          → getExceptionHandlerMethod()
            → 先查 DemoController 本身（无 @ExceptionHandler）
            → 再查 @ControllerAdvice（找到 GlobalExceptionHandler）
              → ExceptionHandlerMethodResolver#resolveMethodByThrowable()
                → 匹配 NullPointerException.class → handleNullPointer()
```

### 实验 4.2：自定义 BusinessException

```bash
curl http://localhost:8080/demo/ex/business
```

**预期返回**：
```json
{
  "error": "BusinessException",
  "errorCode": "ORDER_NOT_FOUND",
  "message": "Order #12345 does not exist",
  "status": 422
}
```

**断点**：`ExceptionHandlerMethodResolver#resolveMethodByThrowable()`
**观察**：深度优先匹配，BusinessException 精确匹配优先于 Exception 兜底

### 实验 4.3：@ResponseStatus 触发不同的 Resolver

```bash
curl -v http://localhost:8080/demo/ex/accessDenied
```

**预期**：HTTP 403 Forbidden

**关键区别**：这个异常**不是**被 `ExceptionHandlerExceptionResolver` 处理的！

**断点链**：
```
processHandlerException()
  → ExceptionHandlerExceptionResolver#resolveException() → 返回 null（虽然有 Exception 兜底，但先检查 @ResponseStatus）
  → ResponseStatusExceptionResolver#doResolveException()
    → 发现 ForbiddenException 上有 @ResponseStatus(403)
    → response.sendError(403, "Access Denied")
```

> 💡 **思考**：如果你在 `GlobalExceptionHandler` 中也加了 `@ExceptionHandler(ForbiddenException.class)`，哪个会先生效？
> 答案：`ExceptionHandlerExceptionResolver` 排在 `ResponseStatusExceptionResolver` 前面，所以 `@ExceptionHandler` 会先匹配到。

### 实验 4.4：IllegalArgumentException

```bash
curl http://localhost:8080/demo/ex/illegalarg
```

**预期返回**：
```json
{
  "error": "IllegalArgumentException",
  "message": "Invalid parameter: id must be positive",
  "status": 400
}
```

---

## 实验五：返回值处理（对应文档 ⑤）

### 实验 5.1：String 返回值

```bash
curl http://localhost:8080/demo/return/string
```

**断点**：
- `RequestResponseBodyMethodProcessor#handleReturnValue()`
- `StringHttpMessageConverter#writeInternal()` ← 直接写字符串

### 实验 5.2：Map → JSON

```bash
curl http://localhost:8080/demo/return/map
```

**断点**：
- `MappingJackson2HttpMessageConverter#writeInternal()` ← Jackson 序列化

### 实验 5.3：ResponseEntity（自定义头和状态码）

```bash
curl -v http://localhost:8080/demo/return/entity
```

**预期**：
- HTTP 201 Created（不是 200）
- 响应头包含 `X-Custom-Header: debug-demo`

**断点**：`HttpEntityMethodProcessor#handleReturnValue()`
**注意**：这用的是 `HttpEntityMethodProcessor`，而不是 `RequestResponseBodyMethodProcessor`！

---

## 实验六：完整请求链路追踪（对应文档 ② DispatcherServlet）

### 🎯 目标
用一个简单请求，完整跟踪 `doDispatch()` 的每一步。

```bash
curl "http://localhost:8080/demo/param?name=test&age=18"
```

### 断点设置（按执行顺序）

| 序号 | 位置 | 对应 doDispatch 步骤 |
|:---:|------|---------------------|
| 1 | `DispatcherServlet#doDispatch()` L1016 | 入口 |
| 2 | `DispatcherServlet#getHandler()` L1230 | 步骤①：通过 HandlerMapping 找到 Handler |
| 3 | `DispatcherServlet#getHandlerAdapter()` L1264 | 步骤②：通过 HandlerAdapter 适配 Handler |
| 4 | `HandlerExecutionChain#applyPreHandle()` | 步骤③：执行 Interceptor preHandle |
| 5 | `RequestMappingHandlerAdapter#handleInternal()` | 步骤④：执行 Handler 方法 |
| 6 | `HandlerExecutionChain#applyPostHandle()` | 步骤⑤：执行 Interceptor postHandle |
| 7 | `DispatcherServlet#processDispatchResult()` | 步骤⑥：处理结果（视图渲染/异常处理） |
| 8 | `HandlerExecutionChain#triggerAfterCompletion()` | 步骤⑦：Interceptor afterCompletion |

---

## 文件清单

| 文件 | 用途 | 对应文档 |
|------|------|---------|
| `demo/DemoController.java` | 综合 Controller，覆盖参数解析/数据绑定/异常触发/返回值类型 | ⑤⑥⑦ |
| `demo/GlobalExceptionHandler.java` | @ControllerAdvice 全局异常处理 | ⑦ |
| `demo/BusinessException.java` | 自定义业务异常 | ⑦ |
| `demo/ForbiddenException.java` | @ResponseStatus 异常 | ⑦ |
| `demo/LoggingFilter.java` | Filter 链日志（洋葱模型） | ⑧ |
| `demo/AuthFilter.java` | Filter 短路演示 | ⑧ |
| `demo/LoggingInterceptor.java` | Interceptor 生命周期日志 | ⑧ |
| `demo/AuthInterceptor.java` | Interceptor preHandle=false 演示 | ⑧ |
| `WebMvcConfig.java` | 注册 Interceptor（已修改） | ⑧ |
| `MyWebAppInitializer.java` | 注册 Filter（已修改） | ⑧ |
| `RootConfig.java` | 父容器排除 @ControllerAdvice（已修改） | ① |
