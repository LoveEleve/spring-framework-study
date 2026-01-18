package com.debug.demo;

import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.*;
import java.util.*;

/**
 * 模拟Spring Boot自动配置机制的演示
 *
 * 展示如何使用DeferredImportSelector实现类似Spring Boot的自动配置
 * 包括条件注解、配置属性、自动配置类等概念
 */
public class SpringBootStyleAutoConfigDemo {

	public static void main(String[] args) {
		System.out.println("=== Spring Boot风格自动配置演示 ===");

		// 模拟不同的环境配置
		System.setProperty("app.datasource.enabled", "true");
		System.setProperty("app.datasource.type", "mysql");
		System.setProperty("app.cache.enabled", "true");
		System.setProperty("app.web.enabled", "false");

		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BootApplication.class);

		// 查看自动配置结果
		System.out.println("\n=== 自动配置结果 ===");
		String[] beanNames = context.getBeanDefinitionNames();
		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			if (!beanName.startsWith("org.springframework")) {
				Object bean = context.getBean(beanName);
				System.out.println("  ✓ " + beanName + " : " + bean);
			}
		}

		context.close();
	}

	/**
	 * 启动类 - 模拟Spring Boot的@SpringBootApplication
	 */
	@Configuration
	@EnableAutoConfiguration
	static class BootApplication {

		@Bean
		public String applicationInfo() {
			return "Spring Boot Style Auto Configuration Demo";
		}
	}

	/**
	 * 启用自动配置注解 - 模拟@EnableAutoConfiguration
	 */
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Import(AutoConfigurationImportSelector.class)
	@interface EnableAutoConfiguration {
		/**
		 * 排除的自动配置类
		 */
		Class<?>[] exclude() default {};

		/**
		 * 排除的自动配置类名
		 */
		String[] excludeName() default {};
	}

	/**
	 * 自动配置导入选择器 - 模拟Spring Boot的AutoConfigurationImportSelector
	 */
	static class AutoConfigurationImportSelector implements DeferredImportSelector {

		@Override
		public String[] selectImports(AnnotationMetadata importingClassMetadata) {
			// 在实际Spring Boot中，这里会从META-INF/spring.factories读取配置
			return getAutoConfigurations();
		}

		@Override
		public Class<? extends Group> getImportGroup() {
			return AutoConfigurationGroup.class;
		}

		/**
		 * 获取自动配置类列表 - 模拟从spring.factories读取
		 */
		private String[] getAutoConfigurations() {
			// 在真实的Spring Boot中，这些配置来自META-INF/spring.factories文件
			return new String[]{
					DataSourceAutoConfiguration.class.getName(),
					CacheAutoConfiguration.class.getName(),
					WebAutoConfiguration.class.getName(),
					SecurityAutoConfiguration.class.getName()
			};
		}
	}

	/**
	 * 自动配置分组 - 处理条件评估和排序
	 */
	static class AutoConfigurationGroup implements DeferredImportSelector.Group {

		private final List<Entry> imports = new ArrayList<>();
		private final Map<String, AnnotationMetadata> configurations = new LinkedHashMap<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			System.out.println("\n=== 处理自动配置 ===");

			String[] importClassNames = selector.selectImports(metadata);

			for (String importClassName : importClassNames) {
				// 模拟条件评估
				if (shouldInclude(importClassName)) {
					configurations.put(importClassName, metadata);
					imports.add(new Entry(metadata, importClassName));
					System.out.println("  ✓ 包含自动配置: " + getSimpleName(importClassName));
				} else {
					System.out.println("  ✗ 跳过自动配置: " + getSimpleName(importClassName) + " (条件不满足)");
				}
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			System.out.println("\n=== 确定最终自动配置列表 ===");

			// 模拟Spring Boot的自动配置排序
			imports.sort((e1, e2) -> {
				String name1 = getSimpleName(e1.getImportClassName());
				String name2 = getSimpleName(e2.getImportClassName());
				return name1.compareTo(name2);
			});

			System.out.println("最终自动配置顺序:");
			for (Entry entry : imports) {
				System.out.println("  → " + getSimpleName(entry.getImportClassName()));
			}

			return imports;
		}

		/**
		 * 模拟条件评估 - 检查是否应该包含某个自动配置
		 */
		private boolean shouldInclude(String className) {
			String simpleName = getSimpleName(className);

			switch (simpleName) {
				case "DataSourceAutoConfiguration":
					return Boolean.parseBoolean(System.getProperty("app.datasource.enabled", "false"));
				case "CacheAutoConfiguration":
					return Boolean.parseBoolean(System.getProperty("app.cache.enabled", "false"));
				case "WebAutoConfiguration":
					return Boolean.parseBoolean(System.getProperty("app.web.enabled", "false"));
				case "SecurityAutoConfiguration":
					return Boolean.parseBoolean(System.getProperty("app.security.enabled", "false"));
				default:
					return true;
			}
		}

		private String getSimpleName(String className) {
			return className.substring(className.lastIndexOf('.') + 1);
		}
	}

	// ==================== 自动配置类 ====================

	/**
	 * 数据源自动配置
	 */
	@Configuration
	@ConditionalOnProperty(name = "app.datasource.enabled", havingValue = "true")
	static class DataSourceAutoConfiguration {

		@Bean
		@ConditionalOnProperty(name = "app.datasource.type", havingValue = "mysql")
		public String mysqlDataSource() {
			System.out.println("[DataSource] 创建MySQL数据源");
			return "MySQL DataSource (url=jdbc:mysql://localhost:3306/demo)";
		}

		@Bean
		@ConditionalOnProperty(name = "app.datasource.type", havingValue = "postgresql")
		public String postgresqlDataSource() {
			System.out.println("[DataSource] 创建PostgreSQL数据源");
			return "PostgreSQL DataSource (url=jdbc:postgresql://localhost:5432/demo)";
		}

		@Bean
		public String transactionManager() {
			System.out.println("[DataSource] 创建事务管理器");
			return "DataSource Transaction Manager";
		}
	}

	/**
	 * 缓存自动配置
	 */
	@Configuration
	@ConditionalOnProperty(name = "app.cache.enabled", havingValue = "true")
	static class CacheAutoConfiguration {

		@Bean
		public String cacheManager() {
			System.out.println("[Cache] 创建缓存管理器");
			return "Redis Cache Manager (host=localhost:6379)";
		}

		@Bean
		public String cacheTemplate() {
			System.out.println("[Cache] 创建缓存模板");
			return "Redis Template";
		}
	}

	/**
	 * Web自动配置
	 */
	@Configuration
	@ConditionalOnProperty(name = "app.web.enabled", havingValue = "true")
	static class WebAutoConfiguration {

		@Bean
		public String webMvcConfigurer() {
			System.out.println("[Web] 创建Web MVC配置器");
			return "Web MVC Configurer (port=8080)";
		}

		@Bean
		public String restTemplate() {
			System.out.println("[Web] 创建REST模板");
			return "Rest Template";
		}

		@Bean
		public String errorController() {
			System.out.println("[Web] 创建错误控制器");
			return "Basic Error Controller";
		}
	}

	/**
	 * 安全自动配置
	 */
	@Configuration
	@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true")
	static class SecurityAutoConfiguration {

		@Bean
		public String securityFilterChain() {
			System.out.println("[Security] 创建安全过滤器链");
			return "Default Security Filter Chain";
		}

		@Bean
		public String authenticationManager() {
			System.out.println("[Security] 创建认证管理器");
			return "In-Memory Authentication Manager";
		}
	}

	// ==================== 条件注解模拟 ====================

	/**
	 * 模拟@ConditionalOnProperty注解
	 */
	@Target({ElementType.TYPE, ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	@Conditional(OnPropertyCondition.class)
	@interface ConditionalOnProperty {
		String name();

		String havingValue() default "";

		boolean matchIfMissing() default false;
	}

	/**
	 * 属性条件评估器
	 */
	static class OnPropertyCondition implements Condition {

		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnProperty.class.getName());

			if (attributes == null) {
				return true;
			}

			String propertyName = (String) attributes.get("name");
			String expectedValue = (String) attributes.get("havingValue");
			boolean matchIfMissing = (Boolean) attributes.get("matchIfMissing");

			String actualValue = System.getProperty(propertyName);

			if (actualValue == null) {
				return matchIfMissing;
			}

			if (expectedValue.isEmpty()) {
				return !actualValue.isEmpty();
			}

			boolean matches = expectedValue.equals(actualValue);

			// 输出条件评估结果
			String elementName = "";
			if (metadata instanceof org.springframework.core.type.MethodMetadata) {
				elementName = ((org.springframework.core.type.MethodMetadata) metadata).getMethodName();
			} else if (metadata instanceof org.springframework.core.type.ClassMetadata) {
				String className = ((org.springframework.core.type.ClassMetadata) metadata).getClassName();
				elementName = className.substring(className.lastIndexOf('.') + 1);
			}

			System.out.println("    [条件评估] " + elementName +
					" - 属性 " + propertyName + "=" + actualValue +
					" (期望: " + expectedValue + ") → " + (matches ? "匹配" : "不匹配"));

			return matches;
		}
	}
}