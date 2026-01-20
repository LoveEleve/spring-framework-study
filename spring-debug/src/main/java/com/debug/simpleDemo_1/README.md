# processConfigBeanDefinitions 简单Debug Demo

## 🎯 Demo结构

```
simpleDemo_1/
├── MainConfig.java              # 主配置类(Full配置类)
├── DatabaseConfig.java          # 导入的配置类(Lite配置类)  
├── MyImportSelector.java        # ImportSelector实现
├── CacheConfig.java            # ImportSelector导入的配置类
├── service/
│   └── ProductService.java     # @ComponentScan扫描的组件
├── SimpleDebugApp.java         # 启动类
└── README.md                   # 说明文档
```

## 🔍 核心功能覆盖

### 1. 配置类类型
- **Full配置类**: `MainConfig` (@Configuration，会被CGLIB代理)
- **Lite配置类**: `DatabaseConfig` (@Configuration(proxyBeanMethods=false)，不会被代理)

### 2. 核心注解处理
- **@ComponentScan**: 扫描`service`包，立即注册`ProductService`
- **@Import**: 导入`DatabaseConfig`和`MyImportSelector`
- **@Bean**: 各配置类中的Bean方法，延迟到加载阶段处理

### 3. ImportSelector机制
- `MyImportSelector`立即处理，导入`CacheConfig`

## 🚀 Debug步骤

### 第一步：设置关键断点

```java
// 1. 主流程入口 - 观察整体流程
ConfigurationClassPostProcessor.processConfigBeanDefinitions():283

// 2. 配置类检查 - 观察Full/Lite类型区分
ConfigurationClassUtils.checkConfigurationClassCandidate():117

// 3. 解析阶段 - 观察do-while循环
ConfigurationClassParser.parse():181

// 4. @Import处理 - 观察ImportSelector处理
ConfigurationClassParser.processImports():794

// 5. 加载阶段 - 观察BeanDefinition创建
ConfigurationClassBeanDefinitionReader.loadBeanDefinitions():126

// 6. @Bean方法处理 - 观察Bean注册
ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod():190
```

### 第二步：运行Debug

```bash
# 在IDEA中运行SimpleDebugApp.main()
# 或者命令行运行
java com.debug.simpleDemo_1.SimpleDebugApp
```

### 第三步：观察关键变量

#### 在processConfigBeanDefinitions()中：
- `candidateNames`: 初始BeanDefinition名称数组
- `configCandidates`: 配置类候选者列表
- `candidates`: 待解析的配置类集合
- `alreadyParsed`: 已解析的配置类集合

#### 在ConfigurationClassParser中：
- `configurationClasses`: 解析后的配置类Map
- `importStack`: 导入栈(循环检测)

## 📊 预期执行流程

### 第一阶段：查找配置类候选者
```
1. 获取所有BeanDefinition: [6个后置处理器 + MainConfig]
2. 检查MainConfig: 
   - 有@Configuration注解 -> Full配置类
   - 设置CONFIGURATION_CLASS_ATTRIBUTE = "full"
3. 排序并添加到configCandidates
```

### 第二阶段：解析配置类 (第一次do-while循环)
```
解析MainConfig:
├── @ComponentScan处理:
│   └── 扫描service包 -> 立即注册ProductService
├── @Import处理:
│   ├── DatabaseConfig -> 作为配置类递归解析
│   └── MyImportSelector -> 立即调用selectImports() -> 导入CacheConfig
└── @Bean方法收集:
    ├── userService -> 添加到beanMethods
    └── orderService -> 添加到beanMethods

解析DatabaseConfig:
└── @Bean方法收集:
    └── dataSource -> 添加到beanMethods

解析CacheConfig:
└── @Bean方法收集:
    └── cacheManager -> 添加到beanMethods
```

### 第三阶段：加载BeanDefinition
```
遍历所有ConfigurationClass:
├── MainConfig (isImported=false):
│   ├── userService @Bean -> 创建ConfigurationClassBeanDefinition
│   └── orderService @Bean -> 创建ConfigurationClassBeanDefinition
├── DatabaseConfig (isImported=true):
│   ├── 注册自身为BeanDefinition
│   └── dataSource @Bean -> 创建ConfigurationClassBeanDefinition
└── CacheConfig (isImported=true):
    ├── 注册自身为BeanDefinition
    └── cacheManager @Bean -> 创建ConfigurationClassBeanDefinition
```

## 🎯 关键观察点

### 1. 配置类类型区分
- 观察`CONFIGURATION_CLASS_ATTRIBUTE`的设置
- Full vs Lite的不同处理方式

### 2. 立即vs延迟处理
- @ComponentScan: 立即扫描注册
- @Bean方法: 延迟到加载阶段

### 3. 导入配置类的处理
- `isImported()`标记
- 导入的配置类需要注册自身

### 4. do-while循环机制
- 第一次循环处理主配置类
- 检查是否有新配置类被引入

## 🔧 Debug技巧

### 条件断点
```java
// 只在处理MainConfig时停止
configClass.getMetadata().getClassName().contains("MainConfig")

// 只在处理@Bean方法时停止
methodName.equals("userService")
```

### 表达式求值
```java
// 查看配置类类型
beanDef.getAttribute(ConfigurationClassUtils.CONFIGURATION_CLASS_ATTRIBUTE)

// 查看是否为导入的配置类
configClass.isImported()

// 查看@Bean方法数量
configClass.getBeanMethods().size()
```

这个简化的Demo专注于核心逻辑，去掉了复杂的DeferredImportSelector、ImportBeanDefinitionRegistrar、XML等功能，让你能够清晰地观察`processConfigBeanDefinitions`的核心处理流程。