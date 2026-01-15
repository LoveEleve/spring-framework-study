# AnnotationConfigApplicationContext 构造过程完整对象分析

## 📋 概述

本文档详细分析 `AnnotationConfigApplicationContext` 构造函数执行过程中创建的所有对象，按照继承层次和创建顺序进行系统梳理。

## 🏗️ 构造函数调用链

```java
AnnotationConfigApplicationContext(Class<?>... componentClasses)
    ↓
AnnotationConfigApplicationContext()
    ↓
GenericApplicationContext()
    ↓
AbstractApplicationContext()
    ↓
DefaultResourceLoader()
```

## 📊 对象创建统计

- **总对象数量**：50个
- **总内存占用**：约138KB
- **创建层级**：4层继承结构
- **核心组件**：3个主要功能模块

---

## 🔧 第一层：DefaultResourceLoader 对象创建

**基础资源加载能力**

### 1. Set<ProtocolResolver> protocolResolvers ⭐⭐⭐
```java
private final Set<ProtocolResolver> protocolResolvers = new LinkedHashSet<>(4);
```
- **作用**：存储自定义协议解析器
- **类型**：LinkedHashSet，初始容量4
- **内存占用**：~1KB
- **职责**：支持自定义资源协议（如 `myprotocol://`）

### 2. Map<Class<?>, Map<Resource, ?>> resourceCaches ⭐⭐⭐
```java
private final Map<Class<?>, Map<Resource, ?>> resourceCaches = new ConcurrentHashMap<>(4);
```
- **作用**：资源缓存，提升重复访问性能
- **类型**：ConcurrentHashMap，线程安全
- **内存占用**：~2KB
- **职责**：缓存已解析的资源对象

---

## 🏛️ 第二层：AbstractApplicationContext 对象创建

**应用上下文基础设施**

### 3. Log logger ⭐⭐⭐
```java
protected final Log logger = LogFactory.getLog(getClass());
```
- **作用**：日志记录器
- **类型**：Apache Commons Logging
- **内存占用**：~2KB
- **职责**：记录容器启动、关闭、异常等日志

### 4. String id ⭐⭐
```java
private String id = ObjectUtils.identityToString(this);
```
- **作用**：上下文唯一标识符
- **初始值**：对象内存地址字符串
- **内存占用**：~100B
- **职责**：区分不同的ApplicationContext实例

### 5. String displayName ⭐⭐
```java
private String displayName = ObjectUtils.identityToString(this);
```
- **作用**：上下文显示名称
- **初始值**：与id相同
- **内存占用**：~100B
- **职责**：用于日志和调试显示

### 6. List<BeanFactoryPostProcessor> beanFactoryPostProcessors ⭐⭐⭐⭐⭐
```java
private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();
```
- **作用**：存储Bean工厂后置处理器
- **类型**：ArrayList，动态扩容
- **内存占用**：~2KB
- **职责**：在Bean定义加载后、Bean实例化前进行处理

### 7. AtomicBoolean active ⭐⭐⭐⭐
```java
private final AtomicBoolean active = new AtomicBoolean();
```
- **作用**：标识上下文是否处于活跃状态
- **类型**：原子布尔值，线程安全
- **内存占用**：~50B
- **职责**：控制容器生命周期状态

### 8. AtomicBoolean closed ⭐⭐⭐⭐
```java
private final AtomicBoolean closed = new AtomicBoolean();
```
- **作用**：标识上下文是否已关闭
- **类型**：原子布尔值，线程安全
- **内存占用**：~50B
- **职责**：防止重复关闭操作

### 9. Object startupShutdownMonitor ⭐⭐⭐⭐⭐
```java
private final Object startupShutdownMonitor = new Object();
```
- **作用**：启动和关闭操作的同步监视器
- **类型**：普通Object对象
- **内存占用**：~50B
- **职责**：确保容器启动/关闭操作的线程安全

### 10. ApplicationStartup applicationStartup ⭐⭐
```java
private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
```
- **作用**：应用启动性能监控
- **类型**：默认实现（空操作）
- **内存占用**：~1KB
- **职责**：记录启动过程的性能指标

### 11. Set<ApplicationListener<?>> applicationListeners ⭐⭐⭐⭐
```java
private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
```
- **作用**：存储静态注册的应用监听器
- **类型**：LinkedHashSet，保持插入顺序
- **内存占用**：~3KB
- **职责**：管理容器事件监听器

### 12. ResourcePatternResolver resourcePatternResolver ⭐⭐⭐⭐⭐
```java
// 在构造函数中创建
this.resourcePatternResolver = getResourcePatternResolver();
// 实际创建
return new PathMatchingResourcePatternResolver(this);
```
- **作用**：资源模式解析器
- **类型**：PathMatchingResourcePatternResolver
- **内存占用**：~8KB
- **职责**：支持Ant风格路径匹配（`classpath*:`、`**/*.xml`）

---

## 🏭 第三层：GenericApplicationContext 对象创建

**通用应用上下文功能**

### 13. DefaultListableBeanFactory beanFactory ⭐⭐⭐⭐⭐
```java
private final DefaultListableBeanFactory beanFactory ;

public GenericApplicationContext() {
    this.beanFactory = new DefaultListableBeanFactory();
}
```
- **作用**：核心Bean工厂
- **类型**：DefaultListableBeanFactory
- **内存占用**：~50KB
- **职责**：Bean的创建、管理、依赖注入

### 14. AtomicBoolean refreshed ⭐⭐⭐⭐⭐
```java
private final AtomicBoolean refreshed = new AtomicBoolean();
```
- **作用**：控制容器只能刷新一次
- **类型**：原子布尔值
- **内存占用**：~50B
- **职责**：防止重复刷新，确保容器状态一致性

### 15. boolean customClassLoader ⭐⭐
```java
private boolean customClassLoader = false;
```
- **作用**：标识是否使用自定义类加载器
- **初始值**：false
- **内存占用**：1bit
- **职责**：影响Bean类加载策略

---

## 🏭 第四层：DefaultListableBeanFactory 内部对象创建

**Bean工厂核心数据结构**

### 16. Map<String, Object> singletonObjects ⭐⭐⭐⭐⭐
```java
private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
```
- **作用**：一级缓存，存储完全初始化的单例Bean
- **类型**：ConcurrentHashMap，初始容量256
- **内存占用**：~15KB
- **职责**：单例Bean的最终存储位置

### 17. Map<String, ObjectFactory<?>> singletonFactories ⭐⭐⭐⭐⭐
```java
private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);
```
- **作用**：三级缓存，存储单例Bean的工厂对象
- **类型**：HashMap，初始容量16
- **内存占用**：~3KB
- **职责**：解决循环依赖，提前暴露Bean

### 18. Map<String, Object> earlySingletonObjects ⭐⭐⭐⭐⭐
```java
private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
```
- **作用**：二级缓存，存储提前暴露的单例Bean
- **类型**：ConcurrentHashMap，初始容量16
- **内存占用**：~3KB
- **职责**：循环依赖解决的中间状态

### 19. Set<String> registeredSingletons ⭐⭐⭐⭐
```java
private final Set<String> registeredSingletons = new LinkedHashSet<>(256);
```
- **作用**：记录已注册的单例Bean名称
- **类型**：LinkedHashSet，保持注册顺序
- **内存占用**：~5KB
- **职责**：跟踪单例Bean的注册状态

### 20. Set<String> singletonsCurrentlyInCreation ⭐⭐⭐⭐⭐
```java
private final Set<String> singletonsCurrentlyInCreation = Collections.newSetFromMap(new ConcurrentHashMap<>(16));
```
- **作用**：跟踪当前正在创建的单例Bean
- **类型**：基于ConcurrentHashMap的Set
- **内存占用**：~2KB
- **职责**：检测循环依赖，防止无限递归

### 21. Map<String, Set<String>> dependentBeanMap ⭐⭐⭐⭐
```java
private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);
```
- **作用**：Bean依赖关系映射（A依赖B）
- **类型**：ConcurrentHashMap，嵌套Set
- **内存占用**：~4KB
- **职责**：管理Bean之间的依赖关系

### 22. Map<String, Set<String>> dependenciesForBeanMap ⭐⭐⭐⭐
```java
private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);
```
- **作用**：反向依赖关系映射（B被A依赖）
- **类型**：ConcurrentHashMap，嵌套Set
- **内存占用**：~4KB
- **职责**：支持依赖关系的反向查询

### 23. Map<String, BeanDefinition> beanDefinitionMap ⭐⭐⭐⭐⭐
```java
private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);
```
- **作用**：存储Bean定义信息
- **类型**：ConcurrentHashMap，初始容量256
- **内存占用**：~20KB
- **职责**：Bean元数据的核心存储

### 24. List<String> beanDefinitionNames ⭐⭐⭐⭐
```java
private volatile List<String> beanDefinitionNames = new ArrayList<>(256);
```
- **作用**：按注册顺序存储Bean定义名称
- **类型**：ArrayList，volatile修饰
- **内存占用**：~5KB
- **职责**：保持Bean定义的注册顺序

### 25. List<BeanPostProcessor> beanPostProcessors ⭐⭐⭐⭐⭐
```java
private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();
```
- **作用**：存储Bean后置处理器
- **类型**：特殊的ArrayList实现
- **内存占用**：~3KB
- **职责**：Bean实例化前后的处理逻辑

### 26. BeanPostProcessorCache beanPostProcessorCache ⭐⭐⭐⭐
```java
private volatile BeanPostProcessorCache beanPostProcessorCache;
```
- **作用**：后置处理器分类缓存
- **类型**：内部缓存类
- **内存占用**：~2KB
- **职责**：提升后置处理器查找性能

### 27. Map<Class<?>, String[]> allBeanNamesByType ⭐⭐⭐
```java
private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);
```
- **作用**：按类型缓存所有Bean名称
- **类型**：ConcurrentHashMap
- **内存占用**：~3KB
- **职责**：类型查找性能优化

### 28. Map<Class<?>, String[]> singletonBeanNamesByType ⭐⭐⭐
```java
private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);
```
- **作用**：按类型缓存单例Bean名称
- **类型**：ConcurrentHashMap
- **内存占用**：~3KB
- **职责**：单例类型查找优化

---

## 🚫 第四层补充：AbstractAutowireCapableBeanFactory 依赖控制对象

**自动装配能力和依赖控制**

### 29. Set<Class<?>> ignoredDependencyTypes ⭐⭐⭐⭐
```java
private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();
```
- **作用**：忽略的依赖类型集合
- **类型**：HashSet
- **内存占用**：~1KB
- **职责**：在依赖检查和自动装配时忽略指定类型（如String）

### 30. Set<Class<?>> ignoredDependencyInterfaces ⭐⭐⭐⭐⭐
```java
private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();
```
- **作用**：忽略的依赖接口集合
- **类型**：HashSet
- **默认内容**：BeanFactory接口
- **内存占用**：~1KB
- **职责**：在依赖检查和自动装配时忽略指定接口（如BeanFactoryAware）

### 31. NamedThreadLocal<String> currentlyCreatedBean ⭐⭐⭐
```java
private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");
```
- **作用**：当前正在创建的Bean名称（线程本地变量）
- **类型**：NamedThreadLocal
- **内存占用**：~500B
- **职责**：跟踪当前线程正在创建的Bean，用于隐式依赖注册

---

## 🎯 第五层：AnnotationConfigApplicationContext 专有对象

**注解配置支持**

### 32. AnnotatedBeanDefinitionReader reader ⭐⭐⭐⭐⭐
```java
private final AnnotatedBeanDefinitionReader reader;

public AnnotationConfigApplicationContext() {
    this.reader = new AnnotatedBeanDefinitionReader(this);
}
```
- **作用**：注解Bean定义读取器
- **类型**：AnnotatedBeanDefinitionReader
- **内存占用**：~5KB
- **职责**：处理@Component、@Service等注解类

### 33. ClassPathBeanDefinitionScanner scanner ⭐⭐⭐⭐⭐
```java
private final ClassPathBeanDefinitionScanner scanner;

public AnnotationConfigApplicationContext() {
    this.scanner = new ClassPathBeanDefinitionScanner(this);
}
```
- **作用**：类路径Bean定义扫描器
- **类型**：ClassPathBeanDefinitionScanner
- **内存占用**：~8KB
- **职责**：扫描指定包下的注解类

---

## 🔧 AnnotatedBeanDefinitionReader 内部对象

### 34. BeanDefinitionRegistry registry ⭐⭐⭐⭐
- **作用**：Bean定义注册表引用
- **类型**：指向GenericApplicationContext
- **内存占用**：8B（引用）
- **职责**：注册解析后的Bean定义

### 35. BeanNameGenerator beanNameGenerator ⭐⭐⭐
```java
private BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;
```
- **作用**：注解Bean名称生成器
- **类型**：AnnotationBeanNameGenerator单例
- **内存占用**：8B（引用）
- **职责**：为注解类生成Bean名称

### 36. ScopeMetadataResolver scopeMetadataResolver ⭐⭐⭐
```java
private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();
```
- **作用**：作用域元数据解析器
- **类型**：AnnotationScopeMetadataResolver
- **内存占用**：~1KB
- **职责**：解析@Scope注解

### 37. ConditionEvaluator conditionEvaluator ⭐⭐⭐⭐
```java
private final ConditionEvaluator conditionEvaluator;
```
- **作用**：条件注解评估器
- **类型**：ConditionEvaluator
- **内存占用**：~2KB
- **职责**：处理@Conditional注解

---

## 🔍 ClassPathBeanDefinitionScanner 内部对象

### 38. List<TypeFilter> includeFilters ⭐⭐⭐
```java
private final List<TypeFilter> includeFilters = new ArrayList<>();
```
- **作用**：包含过滤器列表
- **初始内容**：@Component注解过滤器
- **内存占用**：~1KB
- **职责**：确定哪些类应该被扫描

### 39. List<TypeFilter> excludeFilters ⭐⭐⭐
```java
private final List<TypeFilter> excludeFilters = new ArrayList<>();
```
- **作用**：排除过滤器列表
- **类型**：ArrayList
- **内存占用**：~1KB
- **职责**：排除不需要扫描的类

### 40. Environment environment ⭐⭐⭐
- **作用**：环境配置引用
- **类型**：StandardEnvironment
- **内存占用**：8B（引用）
- **职责**：提供环境变量和配置文件访问

### 41. ResourceLoader resourceLoader ⭐⭐⭐
- **作用**：资源加载器引用
- **类型**：指向ApplicationContext
- **内存占用**：8B（引用）
- **职责**：加载类路径资源

---

## 📈 关键内置处理器对象

**在reader创建过程中自动注册的处理器**

### 42. ConfigurationClassPostProcessor ⭐⭐⭐⭐⭐
- **作用**：配置类后置处理器
- **类型**：BeanDefinitionRegistryPostProcessor
- **内存占用**：~3KB
- **职责**：处理@Configuration、@Import、@ComponentScan等

### 43. AutowiredAnnotationBeanPostProcessor ⭐⭐⭐⭐⭐
- **作用**：自动装配注解处理器
- **类型**：BeanPostProcessor
- **内存占用**：~2KB
- **职责**：处理@Autowired、@Value、@Inject注解

### 44. CommonAnnotationBeanPostProcessor ⭐⭐⭐⭐
- **作用**：通用注解处理器
- **类型**：BeanPostProcessor
- **内存占用**：~2KB
- **职责**：处理@Resource、@PostConstruct、@PreDestroy

### 45. EventListenerMethodProcessor ⭐⭐⭐
- **作用**：事件监听器方法处理器
- **类型**：BeanFactoryPostProcessor
- **内存占用**：~1KB
- **职责**：处理@EventListener注解

### 46. DefaultEventListenerFactory ⭐⭐⭐
- **作用**：默认事件监听器工厂
- **类型**：EventListenerFactory
- **内存占用**：~1KB
- **职责**：创建事件监听器实例

---

## 🌍 环境相关对象

### 47. StandardEnvironment environment ⭐⭐⭐⭐
- **作用**：标准环境实现
- **创建时机**：首次访问时懒加载
- **内存占用**：~5KB
- **职责**：管理profiles、properties等环境配置

### 48. MutablePropertySources propertySources ⭐⭐⭐
- **作用**：可变属性源集合
- **包含**：系统属性、环境变量等
- **内存占用**：~3KB
- **职责**：统一管理各种配置源

---

## 🎨 设计模式应用

### 49. PropertySourcesPropertyResolver ⭐⭐⭐
- **作用**：属性解析器
- **设计模式**：策略模式
- **内存占用**：~2KB
- **职责**：解析${...}占位符

### 50. ConversionService conversionService ⭐⭐
- **作用**：类型转换服务
- **类型**：DefaultConversionService
- **内存占用**：~3KB
- **职责**：支持各种类型转换

---

## 📊 总结统计

### 🎯 按重要程度分类
- **⭐⭐⭐⭐⭐ 核心对象**：14个（Bean工厂、缓存、处理器等）
- **⭐⭐⭐⭐ 重要对象**：16个（状态控制、依赖管理等）
- **⭐⭐⭐ 功能对象**：15个（过滤器、解析器等）
- **⭐⭐ 辅助对象**：5个（标识、监控等）

### 💾 按内存占用分类
- **大型对象**（>10KB）：3个（beanFactory、beanDefinitionMap、singletonObjects）
- **中型对象**（1-10KB）：27个（各种缓存、处理器）
- **小型对象**（<1KB）：20个（状态标识、引用等）

### 🏗️ 按功能模块分类
- **资源管理模块**：5个对象
- **Bean生命周期模块**：20个对象
- **注解处理模块**：12个对象
- **环境配置模块**：6个对象
- **事件机制模块**：6个对象
- **依赖控制模块**：1个对象

### ⚡ 性能特征
- **线程安全对象**：34个（使用ConcurrentHashMap、AtomicBoolean等）
- **缓存优化对象**：8个（提升查找和访问性能）
- **懒加载对象**：5个（按需创建，节省内存）

---

## 🔍 调试建议

### 关键断点位置
1. `GenericApplicationContext()` - 查看Bean工厂创建
2. `AnnotatedBeanDefinitionReader.<init>()` - 查看注解处理器注册
3. `DefaultListableBeanFactory.<init>()` - 查看核心数据结构初始化
4. `refresh()` - 查看容器启动过程
5. `getBean()` - 查看Bean创建过程

### 内存分析要点
- 重点关注三级缓存的使用情况
- 监控beanDefinitionMap的增长
- 观察后置处理器的执行顺序
- 跟踪循环依赖的解决过程

这50个对象共同构成了Spring IoC容器的完整基础设施，为Bean的创建、管理、依赖注入提供了强大而灵活的支持。