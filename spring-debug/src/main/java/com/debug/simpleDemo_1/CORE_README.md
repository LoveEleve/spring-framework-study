# processConfigBeanDefinitions 核心功能Demo

## 🎯 专注核心功能

### ✅ 覆盖的核心功能

#### 1. 配置类类型处理
- **Full配置类**: `CoreMainConfig` (@Configuration，CGLIB代理)
- **Lite配置类**: `DatabaseConfig` (@Configuration(proxyBeanMethods=false)，无代理)

#### 2. @Import的四种导入类型
1. **普通配置类导入**: `DatabaseConfig`
2. **ImportSelector立即处理**: `MyImportSelector` → `CacheConfig`  
3. **DeferredImportSelector延迟处理**: `MyDeferredImportSelector` → `AutoConfig`
4. **ImportBeanDefinitionRegistrar编程式注册**: 直接操作Registry

#### 3. @ComponentScan立即处理机制
- 立即扫描`service`包，注册`ProductService`和`NotificationService`
- 与其他注解的延迟处理形成对比

#### 4. @Bean方法处理
- 解析阶段收集到`beanMethods`
- 加载阶段转换为`ConfigurationClassBeanDefinition`
- Full配置类的Bean方法间调用拦截

#### 5. DeferredImportSelector机制
- **延迟处理**：在所有常规配置处理完后执行
- **分组机制**：`MyDeferredImportGroup`自定义分组
- **排序机制**：`@Order`注解支持

#### 6. 两阶段处理机制
- **解析阶段**：收集信息，立即处理@ComponentScan
- **加载阶段**：注册BeanDefinition，处理@Bean方法

#### 7. 循环检测机制
- **do-while循环**：处理新引入的配置类
- **importStack**：循环导入检测

## 🔍 核心执行流程

### 第一阶段：查找配置类候选者
```
1. 获取所有BeanDefinition: [后置处理器们 + CoreMainConfig]
2. 检查CoreMainConfig:
   - 有@Configuration注解 -> Full配置类
   - 设置CONFIGURATION_CLASS_ATTRIBUTE = "full"
3. 排序并添加到configCandidates
```

### 第二阶段：解析配置类

#### 第一次do-while循环
```
解析CoreMainConfig:
├── @ComponentScan处理:
│   └── 扫描service包 -> 立即注册ProductService、NotificationService
├── @Import处理:
│   ├── DatabaseConfig -> 递归解析(Lite配置类)
│   ├── MyImportSelector -> 立即处理 -> 导入CacheConfig
│   ├── MyDeferredImportSelector -> 延迟收集到deferredImportSelectorHandler
│   └── MyImportBeanDefinitionRegistrar -> 收集到importBeanDefinitionRegistrars
└── @Bean方法收集:
    ├── mainService -> 添加到beanMethods
    └── compositeService -> 添加到beanMethods

解析DatabaseConfig:
└── @Bean方法收集:
    └── dataSource -> 添加到beanMethods

解析CacheConfig:
└── @Bean方法收集:
    └── cacheManager -> 添加到beanMethods

DeferredImportSelector延迟处理:
├── 分组处理: MyDeferredImportGroup
├── 排序: @Order(100)
└── MyDeferredImportSelector.selectImports() -> 导入AutoConfig

解析AutoConfig:
└── @Bean方法收集:
    └── autoService -> 添加到beanMethods
```

### 第三阶段：加载BeanDefinition
```
遍历所有ConfigurationClass:
├── CoreMainConfig (isImported=false):
│   ├── mainService @Bean -> 创建ConfigurationClassBeanDefinition
│   └── compositeService @Bean -> 创建ConfigurationClassBeanDefinition
├── DatabaseConfig (isImported=true):
│   ├── 注册自身为BeanDefinition
│   └── dataSource @Bean -> 创建BeanDefinition
├── CacheConfig (isImported=true):
│   ├── 注册自身为BeanDefinition
│   └── cacheManager @Bean -> 创建BeanDefinition
├── AutoConfig (isImported=true):
│   ├── 注册自身为BeanDefinition
│   └── autoService @Bean -> 创建BeanDefinition
└── ImportBeanDefinitionRegistrar处理:
    └── 编程式注册programmaticBean1、programmaticBean2
```

## 🚀 核心Debug断点

### 第一阶段：查找配置类候选者
```java
// 1. 主流程入口
ConfigurationClassPostProcessor.processConfigBeanDefinitions():283

// 2. 配置类检查
ConfigurationClassUtils.checkConfigurationClassCandidate():117
```

### 第二阶段：解析配置类
```java
// 3. 解析阶段入口
ConfigurationClassParser.parse():181

// 4. 单个配置类处理
ConfigurationClassParser.processConfigurationClass():266

// 5. @ComponentScan处理
ComponentScanAnnotationParser.parse()

// 6. @Import处理
ConfigurationClassParser.processImports():794

// 7. DeferredImportSelector延迟处理
ConfigurationClassParser.DeferredImportSelectorHandler.process():1051
```

### 第三阶段：加载BeanDefinition
```java
// 8. 加载阶段入口
ConfigurationClassBeanDefinitionReader.loadBeanDefinitions():126

// 9. 单个配置类加载
ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForConfigurationClass():137

// 10. @Bean方法处理
ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsForBeanMethod():190

// 11. ImportBeanDefinitionRegistrar处理
ConfigurationClassBeanDefinitionReader.loadBeanDefinitionsFromRegistrars()
```

## 🎯 关键观察点

### 1. 配置类类型区分
```java
// ConfigurationClassUtils.checkConfigurationClassCandidate():191-198
if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
    beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_FULL);
} else if (config != null || isConfigurationCandidate(metadata)) {
    beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, CONFIGURATION_CLASS_LITE);
}
```

### 2. @ComponentScan立即处理
```java
// ComponentScanAnnotationParser.parse()
Set<BeanDefinitionHolder> scannedBeanDefinitions = 
    this.componentScanBeanDefinitionParser.parse(componentScan, sourceClass.getMetadata().getClassName());
// 立即注册到容器
```

### 3. @Import四种类型处理
```java
// ConfigurationClassParser.processImports():817-866
if (candidate.isAssignable(ImportSelector.class)) {
    if (selector instanceof DeferredImportSelector) {
        // 延迟处理
    } else {
        // 立即处理
    }
} else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
    // 收集到configClass
} else {
    // 普通配置类递归处理
}
```

### 4. DeferredImportSelector延迟机制
```java
// ConfigurationClassParser.DeferredImportSelectorHandler.process():1051
// 分组、排序、批量处理
```

### 5. Full配置类CGLIB代理
```java
// ConfigurationClassPostProcessor.enhanceConfigurationClasses()
// Full配置类会被CGLIB增强，支持Bean方法间调用拦截
```

## 🔧 验证要点

### 1. 处理顺序验证
- @ComponentScan: 立即处理
- 普通@Import: 立即递归处理  
- DeferredImportSelector: 最后处理
- ImportBeanDefinitionRegistrar: 加载阶段处理

### 2. Bean注册时机验证
- @ComponentScan扫描组件: 解析阶段立即注册
- @Bean方法: 加载阶段注册
- ImportBeanDefinitionRegistrar: 加载阶段执行

### 3. 配置类自身注册验证
- 主配置类(非导入): 不注册自身
- 导入配置类: 注册自身为BeanDefinition

### 4. CGLIB代理验证
- Full配置类被CGLIB代理
- Bean方法间调用被拦截保证单例

这个精简的核心Demo专注于`processConfigBeanDefinitions`的最重要功能，去掉了XML和条件注解等复杂逻辑，让你能够清晰地观察核心处理流程！