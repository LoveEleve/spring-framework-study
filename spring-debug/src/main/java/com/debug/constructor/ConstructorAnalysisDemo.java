package com.debug.constructor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * AnnotationConfigApplicationContext 无参构造器详细分析
 * 
 * 这个Demo展示了构造器的完整执行流程和创建的对象
 * 
 * @author Spring源码学习者
 */
public class ConstructorAnalysisDemo {

	public static void main(String[] args) {
		System.out.println("========== AnnotationConfigApplicationContext 构造器分析 ==========\n");

		/*
		 * 调用链路：
		 * AnnotationConfigApplicationContext()
		 *   ├─> super() - 调用父类 GenericApplicationContext 构造器
		 *   │   └─> this.beanFactory = new DefaultListableBeanFactory();
		 *   │
		 *   ├─> this.reader = new AnnotatedBeanDefinitionReader(this);
		 *   │   ├─> 创建 ConditionEvaluator（条件注解评估器）
		 *   │   └─> AnnotationConfigUtils.registerAnnotationConfigProcessors(registry)
		 *   │       ├─> 注册 ConfigurationClassPostProcessor（处理@Configuration）
		 *   │       ├─> 注册 AutowiredAnnotationBeanPostProcessor（处理@Autowired）
		 *   │       ├─> 注册 CommonAnnotationBeanPostProcessor（处理@Resource等JSR-250）
		 *   │       ├─> 注册 PersistenceAnnotationBeanPostProcessor（处理JPA注解）
		 *   │       ├─> 注册 EventListenerMethodProcessor（处理@EventListener）
		 *   │       └─> 注册 DefaultEventListenerFactory（事件监听器工厂）
		 *   │
		 *   └─> this.scanner = new ClassPathBeanDefinitionScanner(this);
		 *       └─> 创建包扫描器，默认支持 @Component、@Repository、@Service、@Controller
		 */

		// 【断点位置1】 - 在这行打断点，Step Into 进入构造器
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

		System.out.println("\n========== 构造器执行完成 ==========\n");

		// 查看创建的核心对象
		System.out.println("1. BeanFactory: " + context.getBeanFactory().getClass().getName());
		System.out.println("2. Reader: " + context.toString().contains("reader"));
		System.out.println("3. Scanner: " + context.toString().contains("scanner"));

		// 查看注册的后置处理器数量
		String[] processorNames = context.getBeanFactory().getBeanDefinitionNames();
		System.out.println("\n已注册的BeanDefinition数量: " + processorNames.length);
		System.out.println("\n内置的后置处理器：");
		for (String name : processorNames) {
			System.out.println("  - " + name);
		}

		context.close();
	}
}
