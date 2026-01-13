package com.debug;

/**
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║                     Spring 5.3.x 源码学习指南                                  ║
 * ╠══════════════════════════════════════════════════════════════════════════════╣
 * ║  作者：AI Assistant                                                           ║
 * ║  适用版本：Spring Framework 5.3.x                                             ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * 【学习路线图】
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           第一阶段：IoC 容器基础                              │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ Lesson 01: IoC 容器基础                                                      │
 * │   - 理解控制反转的本质                                                        │
 * │   - 核心入口：AnnotationConfigApplicationContext                             │
 * │   - 重点方法：AbstractApplicationContext#refresh()                           │
 * │                                                                              │
 * │ Lesson 02: 依赖注入                                                          │
 * │   - 三种注入方式：构造器、Setter、字段                                         │
 * │   - 核心类：AutowiredAnnotationBeanPostProcessor                             │
 * │   - 重点方法：DefaultListableBeanFactory#resolveDependency()                 │
 * │                                                                              │
 * │ Lesson 03: Bean 生命周期                                                     │
 * │   - 从实例化到销毁的完整流程                                                   │
 * │   - 核心方法：AbstractAutowireCapableBeanFactory#doCreateBean()              │
 * │   - 重点：initializeBean() 中的各个步骤                                       │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           第二阶段：扩展机制                                   │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ Lesson 04: BeanPostProcessor                                                │
 * │   - Spring 最重要的扩展点                                                     │
 * │   - AOP、@Autowired 都基于此实现                                              │
 * │   - 核心方法：applyBeanPostProcessorsBeforeInitialization()                  │
 * │                                                                              │
 * │ Lesson 07: BeanFactoryPostProcessor                                         │
 * │   - 修改 BeanDefinition 的扩展点                                              │
 * │   - ConfigurationClassPostProcessor 处理 @Configuration                     │
 * │   - 核心方法：invokeBeanFactoryPostProcessors()                              │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 * ┌─────────────────────────────────────────────────────────────────────────────┐
 * │                           第三阶段：高级特性                                   │
 * ├─────────────────────────────────────────────────────────────────────────────┤
 * │ Lesson 05: AOP 面向切面编程                                                  │
 * │   - 5 种通知类型                                                             │
 * │   - 核心类：AnnotationAwareAspectJAutoProxyCreator                          │
 * │   - 代理创建：AbstractAutoProxyCreator#createProxy()                        │
 * │                                                                              │
 * │ Lesson 06: 循环依赖                                                          │
 * │   - 三级缓存机制                                                             │
 * │   - 核心类：DefaultSingletonBeanRegistry                                    │
 * │   - 重点方法：getSingleton()、addSingletonFactory()                         │
 * └─────────────────────────────────────────────────────────────────────────────┘
 *
 *
 * 【核心源码文件清单】
 *
 * IoC 容器核心：
 * ├── spring-beans
 * │   ├── BeanFactory.java                    - Bean 工厂根接口
 * │   ├── DefaultListableBeanFactory.java     - 最核心的 BeanFactory 实现
 * │   ├── DefaultSingletonBeanRegistry.java   - 单例注册表，三级缓存在这里
 * │   └── AbstractAutowireCapableBeanFactory.java - Bean 创建的核心逻辑
 * │
 * └── spring-context
 *     ├── ApplicationContext.java             - 应用上下文接口
 *     ├── AbstractApplicationContext.java     - refresh() 方法在这里！
 *     └── AnnotationConfigApplicationContext.java - 注解配置入口
 *
 * 扩展机制：
 * ├── BeanPostProcessor.java                  - Bean 后置处理器接口
 * ├── BeanFactoryPostProcessor.java           - BeanFactory 后置处理器接口
 * ├── AutowiredAnnotationBeanPostProcessor.java - 处理 @Autowired
 * └── ConfigurationClassPostProcessor.java    - 处理 @Configuration
 *
 * AOP 相关：
 * ├── spring-aop
 * │   ├── AbstractAutoProxyCreator.java       - 自动代理创建器基类
 * │   ├── JdkDynamicAopProxy.java             - JDK 动态代理
 * │   └── CglibAopProxy.java                  - CGLIB 代理
 * │
 * └── AnnotationAwareAspectJAutoProxyCreator.java - AspectJ 注解支持
 *
 *
 * 【refresh() 方法 13 步骤详解】
 *
 * public void refresh() {
 *     // 1. 准备刷新
 *     prepareRefresh();
 *
 *     // 2. 获取 BeanFactory（创建 DefaultListableBeanFactory）
 *     ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();
 *
 *     // 3. 准备 BeanFactory（设置类加载器、添加 BeanPostProcessor 等）
 *     prepareBeanFactory(beanFactory);
 *
 *     // 4. 子类扩展点（空方法，留给子类实现）
 *     postProcessBeanFactory(beanFactory);
 *
 *     // 5. ★★★ 执行 BeanFactoryPostProcessor（处理 @Configuration 等）
 *     invokeBeanFactoryPostProcessors(beanFactory);
 *
 *     // 6. ★★★ 注册 BeanPostProcessor
 *     registerBeanPostProcessors(beanFactory);
 *
 *     // 7. 初始化消息源（国际化）
 *     initMessageSource();
 *
 *     // 8. 初始化事件广播器
 *     initApplicationEventMulticaster();
 *
 *     // 9. 子类扩展点（如 SpringBoot 在这里启动内嵌 Tomcat）
 *     onRefresh();
 *
 *     // 10. 注册监听器
 *     registerListeners();
 *
 *     // 11. ★★★ 实例化所有非懒加载的单例 Bean
 *     finishBeanFactoryInitialization(beanFactory);
 *
 *     // 12. 完成刷新（发布 ContextRefreshedEvent）
 *     finishRefresh();
 * }
 *
 *
 * 【调试技巧】
 *
 * 1. 条件断点：在 getBean() 中设置 beanName.equals("yourBeanName")
 * 2. 方法断点：在接口方法上设置，可以看到所有实现类的调用
 * 3. 异常断点：设置 BeanCreationException 断点，快速定位问题
 * 4. 调用栈：善用 IDEA 的调用栈窗口，理解方法调用链
 *
 *
 * 【运行示例】
 *
 * 每个 Lesson 都可以独立运行，直接运行对应的 main 方法即可。
 * 建议按顺序学习，每个示例都有详细的注释和源码指引。
 *
 * @see com.debug.lesson01_ioc_basic.IoCBasicDemo
 * @see com.debug.lesson02_dependency_injection.DependencyInjectionDemo
 * @see com.debug.lesson03_bean_lifecycle.BeanLifecycleDemo
 * @see com.debug.lesson04_bean_post_processor.BeanPostProcessorDemo
 * @see com.debug.lesson05_aop.AopDemo
 * @see com.debug.lesson06_circular_dependency.CircularDependencyDemo
 * @see com.debug.lesson07_bean_factory_post_processor.BeanFactoryPostProcessorDemo
 */
public class LEARNING_GUIDE {
    // 这是一个文档类，不需要运行
}
