# Spring MVC 源码面试题总结

> **📍 文档导航** | [📚 返回目录](./README.md) | [⬅️ 上一篇：Filter与Interceptor对比](./Filter与Interceptor完整对比深度分析.md)
>
> **📊 元信息** | 难度：⭐⭐⭐（复习向） | 预估时间：0.5天 | 前置阅读：建议完成 [①-⑧](./README.md#21--完整学习路径推荐约-10-15-天)
>
> **🎯 学习目标** | 全系列的串联和收尾，面试前最后过一遍的利器

---

> 📌 基于 8 篇源码深度分析文档提炼，按面试频率排序
> 每题包含：**简答（30秒版）** + **深入追问（源码级）**

---

## 一、请求处理全流程（必考）

### Q1：描述一个 HTTP 请求在 Spring MVC 中的完整处理流程

**简答：**

`Tomcat` 接收请求 → `Filter` 链 → `DispatcherServlet.doDispatch()` → `HandlerMapping` 找到 Handler + 拦截器链 → `HandlerAdapter` 适配调用 → `Interceptor.preHandle()` → 参数解析 → 反射调用 Controller 方法 → 返回值处理 → `Interceptor.postHandle()` → 视图渲染（或 `@ResponseBody` 直接写响应） → `Interceptor.afterCompletion()`

**源码级追问：**

`DispatcherServlet.doDispatch()` 的核心流程：

```
1. getHandler(request)
   → 遍历 handlerMappings，找到第一个返回非 null 的 HandlerExecutionChain
   → 包含 Handler + 匹配的 Interceptor 列表

2. getHandlerAdapter(handler)
   → 遍历 handlerAdapters，找到第一个 supports(handler) 返回 true 的

3. mappedHandler.applyPreHandle()
   → 正序执行拦截器 preHandle()，有一个返回 false 就中断

4. ha.handle(request, response, handler)
   → RequestMappingHandlerAdapter.handleInternal()
   → invokeHandlerMethod()
   → 参数解析 + 反射调用 + 返回值处理

5. mappedHandler.applyPostHandle()
   → 倒序执行拦截器 postHandle()

6. processDispatchResult()
   → 异常处理 / 视图渲染

7. triggerAfterCompletion()
   → 倒序执行拦截器 afterCompletion()（finally 中，一定执行）
```

**继续追问：如果 Handler 找不到会怎样？**

`getHandler()` 返回 null → `noHandlerFound()` → 如果 `throwExceptionIfNoHandlerFound=true` 抛 `NoHandlerFoundException`，否则返回 404。

---

### Q2：DispatcherServlet 的初始化流程？九大组件是什么？

**简答：**

Tomcat 启动 → `Servlet.init()` → `HttpServletBean.init()` → `FrameworkServlet.initServletBean()` → `initWebApplicationContext()` → `onRefresh()` → `DispatcherServlet.initStrategies()` → 初始化九大组件。

**九大组件：**

| 组件 | 作用 | 现代开发常用？ |
|------|------|--------------|
| MultipartResolver | 文件上传 | ✅ |
| LocaleResolver | 国际化 | 少 |
| ThemeResolver | 主题 | ❌ 基本不用 |
| **HandlerMapping** | URL → Handler 映射 | ✅ 核心 |
| **HandlerAdapter** | 调用 Handler | ✅ 核心 |
| **HandlerExceptionResolver** | 异常处理 | ✅ 核心 |
| RequestToViewNameTranslator | 默认视图名 | ❌ 前后端分离不用 |
| ViewResolver | 视图解析 | ❌ 前后端分离不用 |
| FlashMapManager | 重定向传参 | 少 |

**追问：九大组件的初始化策略？**

每个组件初始化逻辑相同：先从 `ApplicationContext` 中按类型查找 Bean → 找不到则从 `DispatcherServlet.properties` 加载默认实现。

---

## 二、HandlerMapping（高频）

### Q3：Spring MVC 如何根据 URL 找到对应的 Controller 方法？

**简答：**

`RequestMappingHandlerMapping` 在启动时扫描所有 `@Controller` 类中的 `@RequestMapping` 方法，建立 `RequestMappingInfo → HandlerMethod` 的映射关系，存储在 `MappingRegistry` 中。请求到来时，根据 URL、HTTP 方法、请求头等条件匹配最佳的 `HandlerMethod`。

**源码级追问：**

1. **注册阶段**（启动时）：
   - `afterPropertiesSet()` → `initHandlerMethods()` → 遍历所有 Bean
   - 通过 `isHandler()` 判断是否有 `@Controller` 或 `@RequestMapping`
   - `detectHandlerMethods()` → 反射扫描每个方法的 `@RequestMapping`
   - 创建 `RequestMappingInfo`（包含 URL 模式、HTTP 方法、参数条件、Header 条件等）
   - 注册到 `MappingRegistry`

2. **匹配阶段**（请求时）：
   - `getHandler(request)` → `getHandlerInternal()` → `lookupHandlerMethod()`
   - 先用 URL 直接匹配（O(1)，`pathLookup` Map）
   - 匹配不到再全量扫描（O(n)）
   - 多个匹配时排序，取最佳匹配；有歧义抛 `IllegalStateException`

**追问：`/users/{id}` 和 `/users/admin` 都匹配 `/users/admin` 时谁优先？**

精确路径 `/users/admin` 优先于模式路径 `/users/{id}`。排序规则：精确匹配 > 路径变量少的 > 通配符少的。

---

### Q4：HandlerMapping 返回的是什么？不是直接返回 Handler 吗？

**简答：**

返回的是 `HandlerExecutionChain`，包含 Handler 本身 + 匹配的 `HandlerInterceptor` 列表。不是直接返回 Handler，因为拦截器需要在 Handler 执行前后介入。

---

## 三、HandlerAdapter（高频）

### Q5：为什么需要 HandlerAdapter？直接调用 Handler 不行吗？

**简答：**

Spring MVC 支持多种 Handler 类型（`@RequestMapping` 方法、`Controller` 接口、`HttpRequestHandler` 等），每种调用方式不同。`HandlerAdapter` 是**适配器模式**的应用，统一了不同 Handler 的调用方式，让 `DispatcherServlet` 不需要关心 Handler 的具体类型。

**追问：有哪些 HandlerAdapter？**

| HandlerAdapter | 处理的 Handler 类型 | 现代常用？ |
|----------------|-------------------|----------|
| `RequestMappingHandlerAdapter` | `@RequestMapping` 方法 | ✅ 最常用 |
| `HttpRequestHandlerAdapter` | `HttpRequestHandler` 接口 | 少（静态资源） |
| `SimpleControllerHandlerAdapter` | `Controller` 接口 | ❌ 过时 |

---

### Q6：RequestMappingHandlerAdapter.invokeHandlerMethod() 做了什么？

**简答：**

```
1. getDataBinderFactory()     → 收集 @InitBinder 方法，创建数据绑定工厂
2. getModelFactory()          → 收集 @ModelAttribute 方法
3. createInvocableHandlerMethod() → 包装成 ServletInvocableHandlerMethod
4. 设置 argumentResolvers / returnValueHandlers / dataBinderFactory
5. modelFactory.initModel()   → 执行 @ModelAttribute 方法
6. invocableMethod.invokeAndHandle() → 参数解析 + 反射调用 + 返回值处理
```

---

## 四、参数解析与返回值处理（必考）

### Q7：Spring MVC 是怎么把请求参数绑定到 Controller 方法参数上的？

**简答：**

通过 `HandlerMethodArgumentResolver` 策略接口。Spring 默认注册了 **27 个参数解析器**（四组优先级），按注册顺序遍历，第一个 `supportsParameter()` 返回 `true` 的解析器负责解析该参数。

**四组优先级：**
1. **注解型**（15个）：`@RequestParam`、`@PathVariable`、`@RequestBody`、`@RequestHeader` 等
2. **类型型**（9个）：`HttpServletRequest`、`Model`、`Errors` 等
3. **自定义**：用户注册的
4. **兜底**（3个）：无注解的简单类型 → `@RequestParam` 处理；无注解的复杂类型 → `@ModelAttribute` 处理

**追问：没有任何注解的 String 参数按什么方式解析？**

被第四组兜底的 `RequestParamMethodArgumentResolver(useDefaultResolution=true)` 处理，等价于 `@RequestParam`，从 `request.getParameter()` 取值。

**追问：没有任何注解的 POJO 参数呢？**

被第四组兜底的 `ServletModelAttributeMethodProcessor(annotationNotRequired=true)` 处理，等价于 `@ModelAttribute`，通过 `WebDataBinder` 逐属性绑定。

---

### Q8：@RequestBody 和 @ModelAttribute 有什么区别？

**简答：**

| 维度 | @RequestBody | @ModelAttribute |
|------|-------------|-----------------|
| 数据来源 | **请求体**（body） | **查询参数/表单参数** + URI 变量 |
| Content-Type | `application/json` 等 | `application/x-www-form-urlencoded` |
| 绑定方式 | `HttpMessageConverter` **整体反序列化** | `WebDataBinder` **逐属性绑定** |
| 底层实现 | `RequestResponseBodyMethodProcessor` | `ModelAttributeMethodProcessor` |
| 错误处理 | 反序列化失败直接抛 `HttpMessageNotReadableException` | 收集到 `BindingResult`，不中断 |

**追问：@RequestBody 底层是怎么反序列化的？**

`readWithMessageConverters()`：获取 Content-Type → 遍历 `HttpMessageConverter` 列表 → 第一个 `canRead(targetType, contentType)` 返回 true 的执行 `read()` → 通常是 `MappingJackson2HttpMessageConverter` 用 Jackson 反序列化。

---

### Q9：@ResponseBody 的返回值是怎么变成 JSON 的？

**简答：**

`RequestResponseBodyMethodProcessor.handleReturnValue()` → `writeWithMessageConverters()`：

1. **内容协商**：获取客户端 Accept 头 → 与服务端可生产的 MediaType 取交集 → 排序选最佳
2. **选择 Converter**：遍历 `HttpMessageConverter`，找到第一个 `canWrite(returnType, mediaType)` 的
3. **序列化写入**：调用 `converter.write()` → Jackson 将对象序列化为 JSON 写入响应体
4. **标记已处理**：`mavContainer.setRequestHandled(true)` → DispatcherServlet 跳过视图解析

---

### Q10：Spring MVC 怎么决定用哪个 HttpMessageConverter？

**简答：**

遍历 `messageConverters` 列表，找第一个满足条件的：
- 读取时：`canRead(参数类型, Content-Type)` 返回 true
- 写入时：`canWrite(返回值类型, Accept协商后的MediaType)` 返回 true

默认注册顺序：`ByteArrayHttpMessageConverter` → `StringHttpMessageConverter` → `SourceHttpMessageConverter` → `AllEncompassingFormHttpMessageConverter` → `MappingJackson2HttpMessageConverter`（Spring Boot 额外添加）

**因为是顺序遍历，注册顺序决定优先级。**

---

## 五、数据绑定与类型转换（中高频）

### Q11：ConversionService 和 PropertyEditor 的区别和优先级？

**简答：**

| 特性 | PropertyEditor（旧） | ConversionService（新） |
|------|---------------------|------------------------|
| 转换方向 | 只能 `String ↔ Object` | 任意 `Object → Object` |
| 线程安全 | ❌ 有状态 | ✅ 无状态 |
| 泛型支持 | ❌ | ✅ |
| 引入版本 | JDK 标准 | Spring 3.0 |

**优先级（从高到低）：**
1. 自定义 PropertyEditor（`@InitBinder` 注册的）
2. ConversionService
3. 默认 PropertyEditor
4. 标准转换规则（String 构造器、Enum 反射等）

**源码依据：** `TypeConverterDelegate.convertIfNecessary()` — 先 `findCustomEditor()`，有则直接用；没有才尝试 `ConversionService`。

---

### Q12：@InitBinder 的作用和执行时机？

**简答：**

`@InitBinder` 标注的方法在**每次创建 `WebDataBinder` 时**被调用，用于自定义数据绑定行为（注册 PropertyEditor、设置允许/禁止的字段等）。

**执行顺序：**
1. `ConfigurableWebBindingInitializer.initBinder()` — 全局初始化（ConversionService、Validator 等）
2. `@ControllerAdvice` 中的 `@InitBinder` — 全局自定义
3. Controller 中的 `@InitBinder` — 局部自定义（可覆盖前两步的设置）

**`@InitBinder("user")` 的 value 属性**：指定只对名为 "user" 的模型属性生效。空值表示对所有绑定器生效。

---

### Q13：WebDataBinder 的安全问题是什么？怎么防御？

**简答：**

默认情况下 `@ModelAttribute` 绑定**所有请求参数**到目标对象，攻击者可以通过构造恶意参数修改不该被修改的字段（如 `id`、`role`、`isAdmin`）。

**防御方式：**
1. `@InitBinder` 中设置 `binder.setAllowedFields("name", "email")` — 白名单
2. `binder.setDisallowedFields("id", "role")` — 黑名单（大小写不敏感，更安全）
3. **最佳实践：使用 DTO**，不直接绑定到实体类

---

## 六、异常处理（必考）

### Q14：Spring MVC 的异常处理机制？@ExceptionHandler 的工作原理？

**简答：**

`DispatcherServlet.processDispatchResult()` 中，如果有异常 → `processHandlerException()` → 遍历 `HandlerExceptionResolver` 链处理。

**三个默认的 ExceptionResolver（按优先级）：**
1. `ExceptionHandlerExceptionResolver` — 处理 `@ExceptionHandler` 方法
2. `ResponseStatusExceptionResolver` — 处理 `@ResponseStatus` 注解
3. `DefaultHandlerExceptionResolver` — 处理 Spring 标准异常（如 `TypeMismatchException` → 400）

**@ExceptionHandler 的匹配规则：**
- 先找 **Controller 本地** 的 `@ExceptionHandler`（优先级高）
- 再找 **`@ControllerAdvice`** 中的（优先级低）
- 匹配最精确的异常类型（子类优先于父类）
- 缓存：第一次匹配结果缓存到 `exceptionHandlerCache`（ConcurrentHashMap），后续 O(1)

**追问：@ExceptionHandler 方法内部又抛异常会怎样？**

当前 ExceptionResolver 放弃处理，交给下一个 Resolver。如果所有 Resolver 都处理不了，异常传播到 Servlet 容器（Tomcat），返回默认错误页面。

---

### Q15：@ControllerAdvice 和 Controller 本地 @ExceptionHandler 谁优先？

**Controller 本地优先。**

源码依据：`ExceptionHandlerExceptionResolver.getExceptionHandlerMethod()` 先查 Controller 类的 `exceptionHandlerCache`，找到直接返回；找不到才遍历 `exceptionHandlerAdviceCache`（`@ControllerAdvice`）。

---

## 七、Filter vs Interceptor（必考）

### Q16：Filter 和 Interceptor 的区别？

**简答：**

| 维度 | Filter | Interceptor |
|------|--------|-------------|
| 规范 | Servlet 规范 | Spring 框架 |
| 位置 | Servlet 容器级别 | DispatcherServlet 内部 |
| 执行时机 | **DispatcherServlet 之前** | **Handler 执行前后** |
| 能否访问 Handler 信息 | ❌ | ✅（preHandle 有 handler 参数） |
| 能否访问 ModelAndView | ❌ | ✅（postHandle 有 modelAndView 参数） |
| 异常处理 | 自行 try-catch | afterCompletion 保证执行 |
| 注入 Spring Bean | 需要 DelegatingFilterProxy | 直接注入 |

**追问：什么场景用 Filter？什么场景用 Interceptor？**

| 场景 | 选择 | 原因 |
|------|------|------|
| 编码设置 / CORS | Filter | 需要在 DispatcherServlet 之前执行 |
| 认证/鉴权 | Filter（Spring Security） | 需要拦截所有请求，包括静态资源 |
| 接口权限检查 | Interceptor | 需要知道目标 Handler 是哪个方法 |
| 日志记录（含方法信息） | Interceptor | 需要 Handler 信息 |
| 请求包装/响应包装 | Filter | 需要在 DispatcherServlet 之前修改请求 |

---

### Q17：Interceptor 的 preHandle 返回 false 后，哪些 afterCompletion 会被调用？

**已经执行过 preHandle 且返回 true 的那些 Interceptor 的 afterCompletion 会被倒序调用。** 当前返回 false 的这个不会调用 afterCompletion。

源码依据：`HandlerExecutionChain.applyPreHandle()` 中记录 `interceptorIndex`，`triggerAfterCompletion()` 从 `interceptorIndex` 倒序遍历。

---

## 八、核心设计模式（常考）

### Q18：Spring MVC 中用到了哪些设计模式？

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **前端控制器** | `DispatcherServlet` | 所有请求的统一入口 |
| **策略模式** | `HandlerMapping` / `HandlerAdapter` / `HandlerMethodArgumentResolver` / `Converter` | 可替换的算法族 |
| **适配器模式** | `HandlerAdapter` | 统一不同 Handler 的调用方式 |
| **组合模式** | `HandlerMethodArgumentResolverComposite` | 统一管理多个解析器 |
| **模板方法** | `AbstractHandlerMapping.getHandler()` / `DataBinder.doBind()` / `DefaultDataBinderFactory.createBinder()` | 定义骨架，子类填充细节 |
| **责任链** | `HandlerInterceptor` 链 / `Filter` 链 / `HttpMessageConverter` 遍历 | 依次处理，可中断 |
| **观察者** | `ContextRefreshedEvent` → `initStrategies()` | 容器刷新时初始化组件 |
| **缓存模式** | `ArgumentResolverComposite` 的 ConcurrentHashMap / `GenericConversionService` 的 ConcurrentReferenceHashMap | 避免重复查找 |

---

## 九、源码细节题（区分度高）

### Q19：HandlerMethodArgumentResolverComposite 为什么有缓存，HandlerMethodReturnValueHandlerComposite 没有？

**ArgumentResolverComposite**：27 个解析器，每次遍历 O(n) 成本高 → 用 `ConcurrentHashMap<MethodParameter, Resolver>(256)` 缓存，后续 O(1)。

**ReturnValueHandlerComposite**：约 15 个处理器，列表短；且异步返回值需要根据运行时 value 动态判断（`isAsyncReturnValue` 依赖实际返回值），无法仅靠 MethodParameter 作为 cache key。

---

### Q20：DispatcherServlet 的 processRequest() 为什么要保存和恢复 LocaleContext / RequestAttributes？

为了支持**嵌套请求**（如 `forward`、`include`）。外层请求设置了 `LocaleContext`，内层请求可能修改它。在 `finally` 中恢复，保证外层请求不受影响。这是**栈帧保存/恢复**的思想。

---

### Q21：为什么 ExceptionHandler 的缓存用 ConcurrentHashMap，而 AdviceCache 用 LinkedHashMap？

- `exceptionHandlerCache`：运行时动态填充（第一次遇到某异常时查找并缓存），需要线程安全 → `ConcurrentHashMap`
- `exceptionHandlerAdviceCache`：启动时一次性填充（`initExceptionHandlerAdviceCache()`），之后只读，不需要并发写 → `LinkedHashMap`（保持 `@Order` 顺序）

---

### Q22：DataBinder 的 allowedFields 匹配大小写敏感，disallowedFields 大小写不敏感，为什么？

**安全设计**：黑名单（disallowedFields）需要更严格，不能被大小写变体绕过。攻击者可能发送 `ID`/`Id`/`iD` 来绕过 `id` 的黑名单，所以 `disallowedFields` 在匹配时将字段名转为小写（`field.toLowerCase()`）。白名单是开发者主动声明的，大小写敏感可以更精确控制。

---

### Q23：GenericConversionService 的 converterCache 用 ConcurrentReferenceHashMap（软引用）而不是 ConcurrentHashMap，为什么？

转换器缓存是**可重建**的——缓存 miss 只是多一次查找，不影响正确性。用软引用可以在 GC 压力大时自动回收缓存项，防止 OOM。如果用 ConcurrentHashMap（强引用），大量不同的类型转换对可能导致缓存无限增长。

---

## 十、综合场景题

### Q24：一个请求 `POST /api/users` 带 JSON body `{"name":"张三","age":18}`，到达 Controller 方法 `createUser(@RequestBody @Valid User user)`，完整经历了哪些步骤？

```
1. Tomcat 接收 TCP 连接，解析 HTTP 报文
2. Filter 链执行（编码、安全等）
3. DispatcherServlet.doDispatch()
4. HandlerMapping 匹配 → HandlerExecutionChain(createUser方法 + interceptors)
5. HandlerAdapter 匹配 → RequestMappingHandlerAdapter
6. Interceptor.preHandle() 正序执行
7. invokeHandlerMethod():
   a. getDataBinderFactory() → 收集 @InitBinder
   b. 创建 ServletInvocableHandlerMethod
   c. invokeAndHandle() → getMethodArgumentValues():
      - 参数 user 有 @RequestBody → RequestResponseBodyMethodProcessor.resolveArgument()
      - readWithMessageConverters():
        · 获取 Content-Type: application/json
        · 遍历 HttpMessageConverter，MappingJackson2HttpMessageConverter.canRead() = true
        · RequestBodyAdvice.beforeBodyRead()（如果有）
        · Jackson ObjectMapper.readValue(inputStream, User.class) → User{name="张三", age=18}
        · RequestBodyAdvice.afterBodyRead()（如果有）
      - @Valid 触发校验：
        · DataBinder.validate() → SmartValidator.validate(user, errors, groups)
        · 如果有校验错误且方法没有 BindingResult 参数 → 抛 MethodArgumentNotValidException
   d. doInvoke() → method.invoke(bean, user) → 执行 Controller 方法
   e. handleReturnValue():
      - 返回值处理器匹配 → RequestResponseBodyMethodProcessor
      - writeWithMessageConverters():
        · 内容协商 → application/json
        · Jackson 序列化返回值 → 写入 response body
      - mavContainer.setRequestHandled(true)
8. Interceptor.postHandle() 倒序执行
9. processDispatchResult() → requestHandled=true → 跳过视图解析
10. Interceptor.afterCompletion() 倒序执行
11. Filter 链返回
12. Tomcat 发送 HTTP 响应
```

---

### Q25：当 Controller 方法抛出自定义异常 `BusinessException` 时，异常是怎么被 @ExceptionHandler 捕获的？

```
1. invokeAndHandle() 中 invokeForRequest() 抛出 BusinessException
2. 异常传播到 doDispatch() 的 catch 块，记录到 dispatchException
3. processDispatchResult(request, response, mappedHandler, null, dispatchException)
4. processHandlerException() → 遍历 HandlerExceptionResolver:
   a. ExceptionHandlerExceptionResolver.resolveException()
   b. 先查 Controller 本地 exceptionHandlerCache → 未命中
   c. 遍历 exceptionHandlerAdviceCache（@ControllerAdvice 中的 @ExceptionHandler）
   d. 找到匹配 BusinessException 的方法（子类精确匹配优先）
   e. 缓存到 exceptionHandlerCache（下次 O(1)）
   f. 反射调用 @ExceptionHandler 方法 → 返回错误响应
5. Interceptor.afterCompletion() 照常执行（异常作为参数传入）
```

---

## 十一、快速对比速查表

### Spring MVC 核心组件一览

| 组件 | 接口 | 默认实现 | 作用 |
|------|------|---------|------|
| HandlerMapping | `HandlerMapping` | `RequestMappingHandlerMapping` | URL → Handler |
| HandlerAdapter | `HandlerAdapter` | `RequestMappingHandlerAdapter` | 调用 Handler |
| ArgumentResolver | `HandlerMethodArgumentResolver` | 27个内置实现 | 参数解析 |
| ReturnValueHandler | `HandlerMethodReturnValueHandler` | 15个内置实现 | 返回值处理 |
| MessageConverter | `HttpMessageConverter` | `MappingJackson2HttpMessageConverter` | 序列化/反序列化 |
| ExceptionResolver | `HandlerExceptionResolver` | `ExceptionHandlerExceptionResolver` | 异常处理 |
| DataBinder | `DataBinder` | `ExtendedServletRequestDataBinder` | 数据绑定 |
| ConversionService | `ConversionService` | `DefaultFormattingConversionService` | 类型转换 |
| Validator | `Validator` | JSR-303 实现 | 数据校验 |

### 缓存策略对比

| 组件 | 缓存类型 | 原因 |
|------|---------|------|
| ArgumentResolverComposite | `ConcurrentHashMap(256)` | 27个解析器，高频查找，key=MethodParameter |
| ReturnValueHandlerComposite | **无缓存** | 15个处理器，短列表 + 异步需要运行时判断 |
| ExceptionHandlerCache (Controller) | `ConcurrentHashMap` | 运行时动态填充，需要线程安全 |
| ExceptionHandlerAdviceCache (@ControllerAdvice) | `LinkedHashMap` | 启动时填充，只读，保持 @Order 顺序 |
| GenericConversionService | `ConcurrentReferenceHashMap(64)` | 软引用，GC 可回收，防 OOM |
| AbstractNamedValueMethodArgumentResolver | `ConcurrentHashMap` | 缓存 NamedValueInfo，避免重复解析注解 |
| RequestMappingHandlerMapping | `pathLookup` Map | URL 直接匹配 O(1) |
