# AnnotationConfigApplicationContext 无参构造器详解

## 🎯 构造器源码

```java
public AnnotationConfigApplicationContext() {
    // 1. 启动监控 - 开始记录
    StartupStep createAnnotatedBeanDefReader = 
        getApplicationStartup().start("spring.context.annotated-bean-reader.create");
    
    // 2. 创建 Bean 定义读取器
    this.reader = new AnnotatedBeanDefinitionReader(this);
    
    // 3. 启动监控 - 结束记录
    createAnnotatedBeanDefReader.end();
    
    // 4. 创建类路径扫描器
    this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```

---

## 📋 完整执行流程

### 🔹 阶段0：调用父类构造器（隐式）

```java
// AnnotationConfigApplicationContext 继承 GenericApplicationContext
public class AnnotationConfigApplicationContext extends GenericApplicationContext {
    // 会先调用父类构造器
}

// GenericApplicationContext 构造器
public GenericApplicationContext() {
    // 创建 Spring 的核心 BeanFactory
    this.beanFactory = new DefaultListableBeanFactory();
}
```

**创建对象**：
- ✅ **DefaultListableBeanFactory** - Spring的IoC容器核心实现

**作用**：
- 负责Bean的注册、创建、依赖注入
- 管理Bean的完整生命周期
- 实现了BeanDefinitionRegistry接口（Bean定义注册中心）

---

### 🔹 阶段1：创建 AnnotatedBeanDefinitionReader

```java
this.reader = new AnnotatedBeanDefinitionReader(this);
```

#### 调用链路：

```java
// AnnotatedBeanDefinitionReader 构造器
public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
    this(registry, getOrCreateEnvironment(registry));
}

public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
    // 1. 保存 registry 引用
    this.registry = registry;
    
    // 2. 创建条件注解评估器
    this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
    
    // 3. 【核心】注册注解配置处理器
    AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
}
```

#### 创建对象：

**1. AnnotatedBeanDefinitionReader 本身**
- **类型**：Bean定义读取器
- **作用**：用于注册通过`register()`方法传入的配置类

**2. ConditionEvaluator（条件评估器）**
- **作用**：评估`@Conditional`注解，决定Bean是否应该被注册

**3. BeanNameGenerator（Bean名称生成器）**
- **默认**：`AnnotationBeanNameGenerator.INSTANCE`
- **作用**：为注册的Bean生成名称

**4. ScopeMetadataResolver（作用域解析器）**
- **默认**：`AnnotationScopeMetadataResolver`
- **作用**：解析`@Scope`注解，确定Bean的作用域

---

### 🔹 阶段2：注册内置的后置处理器（核心）

```java
AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
```

这是**最关键**的一步！注册了Spring注解驱动的核心组件。

#### 注册的6个核心后置处理器：

##### 1️⃣ **ConfigurationClassPostProcessor**
```java
BeanName: "org.springframework.context.annotation.internalConfigurationAnnotationProcessor"
类型: BeanFactoryPostProcessor
```

**作用**：
- ✅ 处理`@Configuration`注解的类
- ✅ 解析`@Bean`方法，注册Bean定义
- ✅ 处理`@ComponentScan`，触发包扫描
- ✅ 处理`@Import`，导入配置类
- ✅ 处理`@ImportResource`，导入XML配置

**为什么重要**：
- 这是Spring注解配置的**核心处理器**
- 没有它，`@Configuration`、`@Bean`等注解都不会生效！

---

##### 2️⃣ **AutowiredAnnotationBeanPostProcessor**
```java
BeanName: "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"
类型: BeanPostProcessor
```

**作用**：
- ✅ 处理`@Autowired`注解（字段、方法、构造器）
- ✅ 处理`@Value`注解
- ✅ 处理JSR-330的`@Inject`注解

**为什么重要**：
- 负责**依赖注入**功能
- 没有它，`@Autowired`不会生效！

---

##### 3️⃣ **CommonAnnotationBeanPostProcessor**
```java
BeanName: "org.springframework.context.annotation.internalCommonAnnotationProcessor"
类型: BeanPostProcessor
条件: 需要JSR-250支持（javax.annotation包）
```

**作用**：
- ✅ 处理`@Resource`注解（按名称注入）
- ✅ 处理`@PostConstruct`注解（初始化方法）
- ✅ 处理`@PreDestroy`注解（销毁方法）
- ✅ 处理`@WebServiceRef`、`@EJB`等

**为什么重要**：
- 支持JSR-250规范的注解
- 提供Bean生命周期回调

---

##### 4️⃣ **PersistenceAnnotationBeanPostProcessor**
```java
BeanName: "org.springframework.context.annotation.internalPersistenceAnnotationProcessor"
类型: BeanPostProcessor
条件: 需要JPA支持（javax.persistence包）
```

**作用**：
- ✅ 处理`@PersistenceContext`注解（注入EntityManager）
- ✅ 处理`@PersistenceUnit`注解（注入EntityManagerFactory）

**为什么重要**：
- 支持JPA注解
- 集成Spring与JPA

---

##### 5️⃣ **EventListenerMethodProcessor**
```java
BeanName: "org.springframework.context.event.internalEventListenerProcessor"
类型: BeanFactoryPostProcessor
```

**作用**：
- ✅ 扫描所有Bean中带有`@EventListener`注解的方法
- ✅ 将这些方法注册为ApplicationListener

**为什么重要**：
- 支持基于注解的事件监听
- 简化事件驱动编程

---

##### 6️⃣ **DefaultEventListenerFactory**
```java
BeanName: "org.springframework.context.event.internalEventListenerFactory"
类型: EventListenerFactory
```

**作用**：
- ✅ 创建事件监听器适配器
- ✅ 包装`@EventListener`方法为ApplicationListener实例

---

### 🔹 阶段3：创建 ClassPathBeanDefinitionScanner

```java
this.scanner = new ClassPathBeanDefinitionScanner(this);
```

#### 创建对象：

**ClassPathBeanDefinitionScanner**
- **作用**：扫描类路径，查找带有特定注解的类并注册为Bean

#### 默认支持的注解：

```java
// ClassPathScanningCandidateComponentProvider 构造器中注册
protected void registerDefaultFilters() {
    // 1. @Component 及其派生注解
    this.includeFilters.add(new AnnotationTypeFilter(Component.class));
    
    // 2. JSR-250 的 @ManagedBean
    if (jsr250Present) {
        this.includeFilters.add(new AnnotationTypeFilter(ManagedBean.class));
    }
    
    // 3. JSR-330 的 @Named
    if (jsr330Present) {
        this.includeFilters.add(new AnnotationTypeFilter(Named.class));
    }
}
```

**支持的注解**：
- ✅ `@Component`
- ✅ `@Service`（继承@Component）
- ✅ `@Repository`（继承@Component）
- ✅ `@Controller`（继承@Component）
- ✅ `@Configuration`（继承@Component）
- ✅ `@ManagedBean`（JSR-250）
- ✅ `@Named`（JSR-330）

---

## 📊 创建对象总结

| 序号 | 对象 | 类型 | 作用 |
|-----|------|------|------|
| 1 | **DefaultListableBeanFactory** | IoC容器 | Bean的注册和管理 |
| 2 | **AnnotatedBeanDefinitionReader** | Bean定义读取器 | 注册配置类 |
| 3 | **ConditionEvaluator** | 条件评估器 | 评估@Conditional |
| 4 | **ClassPathBeanDefinitionScanner** | 包扫描器 | 扫描@Component等 |
| 5 | **ConfigurationClassPostProcessor** | 后置处理器 | 处理@Configuration |
| 6 | **AutowiredAnnotationBeanPostProcessor** | 后置处理器 | 处理@Autowired |
| 7 | **CommonAnnotationBeanPostProcessor** | 后置处理器 | 处理@PostConstruct |
| 8 | **PersistenceAnnotationBeanPostProcessor** | 后置处理器 | 处理JPA注解 |
| 9 | **EventListenerMethodProcessor** | 后置处理器 | 处理@EventListener |
| 10 | **DefaultEventListenerFactory** | 事件监听器工厂 | 创建事件监听器 |

---

## 🎯 核心作用总结

### 1️⃣ **建立IoC容器基础**
- 创建`DefaultListableBeanFactory`作为Bean容器
- 这是Spring的核心，所有Bean都会注册到这里

### 2️⃣ **准备注解处理能力**
- 注册6个核心后置处理器
- 为后续处理`@Configuration`、`@Autowired`等注解做准备

### 3️⃣ **提供两种Bean注册方式**
- **编程式注册**：通过`reader.register(Class<?>... classes)`
- **扫描式注册**：通过`scanner.scan(String... basePackages)`

### 4️⃣ **完成容器预初始化**
- 此时容器已经"就绪"，但还未`refresh()`
- Bean定义还未加载，Bean实例还未创建

---

## 🔍 与其他构造器的对比

### 对比1: 带配置类的构造器

```java
// 无参构造器
public AnnotationConfigApplicationContext() {
    this.reader = new AnnotatedBeanDefinitionReader(this);
    this.scanner = new ClassPathBeanDefinitionScanner(this);
    // 注意：还未注册配置类，还未 refresh()
}

// 带配置类的构造器
public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
    this();              // 1. 调用无参构造器
    register(componentClasses);  // 2. 注册配置类
    refresh();           // 3. 刷新容器（重点！）
}
```

### 对比2: 带包扫描的构造器

```java
public AnnotationConfigApplicationContext(String... basePackages) {
    this();              // 1. 调用无参构造器
    scan(basePackages);  // 2. 扫描包
    refresh();           // 3. 刷新容器
}
```

---

## 💡 使用场景

### 场景1: 手动控制注册和刷新时机

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

// 注册多个配置类
context.register(DataSourceConfig.class);
context.register(ServiceConfig.class);
context.register(WebConfig.class);

// 设置环境
context.getEnvironment().setActiveProfiles("dev");

// 手动刷新
context.refresh();
```

### 场景2: 动态添加Bean

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

// 动态注册单个Bean
context.registerBean("customBean", CustomClass.class, () -> new CustomClass("config"));

context.refresh();
```

### 场景3: 编程式配置

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

// 自定义Bean名称生成器
context.setBeanNameGenerator(new CustomBeanNameGenerator());

// 自定义作用域解析器
context.setScopeMetadataResolver(new CustomScopeResolver());

context.register(AppConfig.class);
context.refresh();
```

---

## 🎓 源码学习断点位置

### 关键断点：

1. **AnnotationConfigApplicationContext构造器** - 第67行
   ```java
   public AnnotationConfigApplicationContext() {
   ```

2. **GenericApplicationContext构造器** - 第114行
   ```java
   this.beanFactory = new DefaultListableBeanFactory();
   ```

3. **AnnotatedBeanDefinitionReader构造器** - 第83行
   ```java
   AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
   ```

4. **AnnotationConfigUtils.registerAnnotationConfigProcessors** - 第148行
   ```java
   public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(...)
   ```

5. **查看注册的后置处理器** - 第163-207行
   ```java
   // ConfigurationClassPostProcessor
   if (!registry.containsBeanDefinition(CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME)) {
       ...
   }
   ```

---

## 🚀 调试建议

### Step 1: 观察父类构造器

```java
AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
// Step Into -> 进入 GenericApplicationContext()
// 观察 DefaultListableBeanFactory 的创建
```

### Step 2: 观察Reader创建

```java
this.reader = new AnnotatedBeanDefinitionReader(this);
// Step Into -> 观察注册了哪些后置处理器
```

### Step 3: 查看BeanDefinition

```java
String[] names = context.getBeanDefinitionNames();
// 此时应该看到6个内置的后置处理器
```

### Step 4: 对比刷新前后

```java
// 刷新前
System.out.println("Before refresh: " + context.getBeanDefinitionCount());

context.register(AppConfig.class);
context.refresh();

// 刷新后
System.out.println("After refresh: " + context.getBeanDefinitionCount());
```

---

## 📖 关键知识点

### 1. 为什么要注册后置处理器？

**答**：后置处理器是Spring注解驱动的基础
- 没有`ConfigurationClassPostProcessor`，`@Configuration`不生效
- 没有`AutowiredAnnotationBeanPostProcessor`，`@Autowired`不生效
- 这些处理器在`refresh()`时被激活

### 2. 为什么构造器不执行refresh()？

**答**：提供灵活性
- 允许在刷新前进行额外配置
- 允许动态注册Bean
- 允许设置环境变量、Profile等

### 3. Reader和Scanner的区别？

**答**：
- **Reader**：用于注册**显式传入**的配置类（`register()`方法）
- **Scanner**：用于**扫描包路径**下的组件类（`scan()`方法）

### 4. 何时会用到无参构造器？

**答**：
- 需要精细控制容器初始化过程
- 需要在刷新前进行额外配置
- 需要动态注册Bean或修改BeanFactory

---

## 🎯 总结

`AnnotationConfigApplicationContext()`无参构造器做了以下事情：

1. ✅ 创建**DefaultListableBeanFactory**（IoC容器核心）
2. ✅ 创建**AnnotatedBeanDefinitionReader**（配置类读取器）
3. ✅ 注册**6个核心后置处理器**（支持注解驱动）
4. ✅ 创建**ClassPathBeanDefinitionScanner**（包扫描器）

**但没有做**：
- ❌ 还未注册任何用户定义的配置类
- ❌ 还未执行`refresh()`刷新容器
- ❌ 还未实例化任何Bean

这是一个**"就绪但未启动"**的状态，为后续的注册和刷新做好了准备！

---

希望这个详细的解释帮助您深入理解构造器的每一个步骤！🎉
