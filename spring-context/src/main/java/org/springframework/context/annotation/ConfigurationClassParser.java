/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.0
 * @see ConfigurationClassBeanDefinitionReader
 */
class ConfigurationClassParser {

	private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

	private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
			(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

	private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR =
			(o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


	private final Log logger = LogFactory.getLog(getClass());

	private final MetadataReaderFactory metadataReaderFactory;

	private final ProblemReporter problemReporter;

	private final Environment environment;

	private final ResourceLoader resourceLoader;

	private final BeanDefinitionRegistry registry;

	private final ComponentScanAnnotationParser componentScanParser;

	private final ConditionEvaluator conditionEvaluator;

	private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

	private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

	private final List<String> propertySourceNames = new ArrayList<>();

	private final ImportStack importStack = new ImportStack();
	// forcus
	/*
		DeferredImportSelectorHandler
		{
			List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>(); 该集合记录着所有的DeferredImportSelector!
		}
	 */

	private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

	private final SourceClass objectSourceClass = new SourceClass(Object.class);


	/**
	 * Create a new {@link ConfigurationClassParser} instance that will be used
	 * to populate the set of configuration classes.
	 */
	public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory,
			ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader,
			BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

		this.metadataReaderFactory = metadataReaderFactory;
		this.problemReporter = problemReporter;
		this.environment = environment;
		this.resourceLoader = resourceLoader;
		this.registry = registry;
		this.componentScanParser = new ComponentScanAnnotationParser(
				environment, resourceLoader, componentScanBeanNameGenerator, registry);
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
	}

	// forcus 解析候选配置类
	public void parse(Set<BeanDefinitionHolder> configCandidates) {
		// 遍历所有的配置类对应的 BeanDefinitionHolder 对象
		for (BeanDefinitionHolder holder : configCandidates) {
			BeanDefinition bd = holder.getBeanDefinition(); // 获取对应的 BeanDefinition
			try {
				/*
					====
					 forcus 根据不同的 BeanDefinition 类型 使用不同的方式进行解析
					  1. AnnotatedBeanDefinition 类型的 : 这是最常见的bf类型(注解驱动)，那么可以直接使用已经解析好的元数据
					  2. AbstractBeanDefinition 类型的,并且有对应的beanClass属性，
					  3. 只有类名
					  不同的情况调用不同的parse()重载方法
					====
				 */
				if (bd instanceof AnnotatedBeanDefinition) {
					parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
				}
				else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
					parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
				}
				else {
					parse(bd.getBeanClassName(), holder.getBeanName());
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
			}
		}
		// forcus 处理延迟的ImportSelector，这是解析的最后一步
		// forcus deferredImportSelectorHandler 中存储着上面解析出来的所有 DeferredImportSelector类
		// forcus 这个方法也是springboot自动配置的核心方法
		/*
			1. 执行的时机,在所有的配置类都解析完毕后
				- 所有常规的配置类都已经解析完毕
				- 所有的 @ComponentScan / @Import / @Bean 注解都已经处理完毕
				{
				在这里指的是@ComponentScan("xxx"),xxx下面的所有相关注解都被处理完了
					@ComponentScan: 所有普通的注解(比如@Component家族)都已经成为了BeanDefinition了
					@Import: 这里会分为3种类型来进行处理
						 - ImportSelector(纯种的)：立即处理,将导入的类当作配置类来进行解析
						 - DeferredImportSelector：被存储在 deferredImportSelectorHandler中的属性
						 - ImportBeanDefinitionRegistrar：被存储在 configClass.importBeanDefinitionRegistrars
					@Bean: 被存储在 configClass.beanMethods 中
				}
				- 此时才处理可能依赖一些配置自动配置
			2. 处理实现了DeferredImportSelector接口的ImportSelector
			3. 典型应用： Spring Boot的自动配置就是通过DeferredImportSelector实现的
			4. 为什么延迟： 确保所有常规配置类都处理完毕后，再处理可能依赖这些配置的自动配置
		 */
		this.deferredImportSelectorHandler.process();
	}

	protected final void parse(@Nullable String className, String beanName) throws IOException {
		Assert.notNull(className, "No bean class name for configuration class bean definition");
		MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
		processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(Class<?> clazz, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
		processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
	}

	/**
	 * Validate each {@link ConfigurationClass} object.
	 * @see ConfigurationClass#validate
	 */
	public void validate() {
		for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
			configClass.validate(this.problemReporter);
		}
	}

	public Set<ConfigurationClass> getConfigurationClasses() {
		return this.configurationClasses.keySet();
	}

	// forcus 解析配置类
	/*
		参数:
		 - configClass:要解析的配置类
		 - filter:过滤器，用于排除某些类（通常是 DEFAULT_EXCLUSION_FILTER ）
	 */
	protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
		// forcus 判断是否需要跳过,此时正处于「解析配置类阶段」 -- PARSE_CONFIGURATION
		// 处理@Conditional注解
		if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
			// 如果条件不满足，直接返回,不再处理这个配置类
			return;
		}
		// forcus  从已处理的配置类集合中查找是否已经存在相同的配置类
		// configurationClasses : 用于存储所有已经处理过的配置类
		ConfigurationClass existingClass = this.configurationClasses.get(configClass);
		// forcus 对于已经处理过的配置类
		if (existingClass != null) {
			// forcus-1 当前配置类是通过@Import导入的
			/*
				@Configuration
				@Import(CommonConfig.class)
				public class AppConfig1 { }

				@Configuration
				@Import(CommonConfig.class)  // 同一个CommonConfig被多次导入
				public class AppConfig2 { }
			 */
			if (configClass.isImported()) { //  isImported() 判断配置类是否通过@Import注解导入
				if (existingClass.isImported()) { // 已存在的也是导入的
					existingClass.mergeImportedBy(configClass); // forcus  同一个配置类被多个地方通过@Import导入,合并导入信息，记录所有导入来源
				}
				// Otherwise ignore new imported config class; existing non-imported class overrides it.
				return; // 已存在的不是导入的,那么直接返回
			}
			// forcus 当前配置类不是导入的 , 发现了显式的Bean定义，可能要替换之前导入的配置类
			// 原则: 显式定义的配置类优先级高于导入的配置类
			else {
				// Explicit bean definition found, probably replacing an import.
				// Let's remove the old one and go with the new one.
				this.configurationClasses.remove(configClass); // 从已处理集合中移除旧的配置类
				this.knownSuperclasses.values().removeIf(configClass::equals); // 从已知父类集合中移除相关引用
			}
		}

		/*
			=====
				forcus 递归处理配置类及其父类层次
			=====
		 */
		// Recursively process the configuration class and its superclass hierarchy.
		/*
			这里为什么要用do-while()?
			 - 处理继承层次： 配置类可能有父类，需要递归处理整个继承链
			 - 返回值机制： doProcessConfigurationClass返回父类的SourceClass，如果没有父类返回null
		 */
		SourceClass sourceClass = asSourceClass(configClass, filter); // 创建SourceClass
		do {
			// forcus 该方法的最后会返回当前类的 sourceClass,递归处理父类
			sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
		}
		while (sourceClass != null);
		// forcus 注册处理完成的配置类
		/*
			 将处理完成的配置类添加到已处理集合中
			 Key和Value都是同一个对象： 这是因为ConfigurationClass重写了equals()和hashCode()方法
			 作用： 标记该配置类已经处理完成，避免重复处理
		 */
		// forcus 这里需要额外注意的一点是,对于被导入的配置类,在解析阶段是没有被注册为BeanDefinition的，而是存放到了这个 configurationClasses 中，在后续的注册阶段才会注册为BeanDefinition
		// forcus 配置类被处理 ≠  注册为BeanDefinition
		// forcus 被@ComponentScan 扫描到的 组件类(@Component/.../) 会被立即注册为BeanDefinition, 对于是被 @Configuration修饰的组件类 还会进行递归解析
		this.configurationClasses.put(configClass, configClass);
	}

	/**
	 * Apply processing and build a complete {@link ConfigurationClass} by reading the
	 * annotations, members and methods from the source class. This method can be called
	 * multiple times as relevant sources are discovered.
	 * @param configClass the configuration class being build
	 * @param sourceClass a source class
	 * @return the superclass, or {@code null} if none found or previously processed
	 */
	/*
		forcus Spring 配置类解析的核心实现
		===
		 - configClass：正在构建的配置类对象，用于收集解析结果
		 - sourceClass：当前正在处理的源类（可能是配置类本身或其父类）
		 - filter：类名过滤器，用于排除某些不需要处理的类
		 - 返回值： 父类的SourceClass对象，如果没有需要处理的父类则返回null
	 */
	@Nullable
	protected final SourceClass doProcessConfigurationClass(
			ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter)
			throws IOException {
		// forcus-1 处理嵌套类
		// 场景如下,但是几乎不会出现,可以不用关心嵌套类的处理
		/*
			@Configuration
			public class OuterConfig {

				@Configuration
				static class InnerConfig {  // 嵌套配置类
					@Bean
					public SomeService someService() {
						return new SomeService();
					}
				}
			}
		 */
		if (configClass.getMetadata().isAnnotated(Component.class.getName())) { // forcus skip
			// Recursively process any member (nested) classes first
			processMemberClasses(configClass, sourceClass, filter);
		}

		// Process any @PropertySource annotations
		// forcus-2 处理 @PropertySource 注解
		/*
		 	@PropertySource 介绍:
		 	 该注解的作用是将外部属性文件加载到 Spring 的 Environment 中，使得可以通过 @Value 或 Environment.getProperty() 来访问这些属性
		 	核心功能:
		 	 - 加载属性文件：指定一个或多个 .properties 或 .yml 文件的位置
		 	 - 注入到环境：将文件中的键值对添加到 Spring 的 Environment 中
		 	 - 支持占位符解析：可以在配置类中使用 ${property.name} 引用这些属性
		 	==== 一个简单的case, 这个注解很重要,但是具体如何解析的可以不用关心,了解即可
				@Configuration
				@PropertySource("classpath:application.properties")
				public class AppConfig {

					@Value("${database.url}")
					private String databaseUrl;

					@Bean
					public DataSource dataSource() {
						// 使用 databaseUrl 创建数据源
					}
				}
		 */
		/*
			处理的基本流程:
			 - 解析注解获取文件路径、编码等属性
			 - 解析路径中的占位符（如 ${config.dir}/app.properties）
			 - 通过 ResourceLoader 加载资源文件
			 - 使用 PropertySourceFactory 创建 PropertySource 对象
			 - 将 PropertySource 添加到 Environment 的 MutablePropertySources 中
		 */
		for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), PropertySources.class,
				org.springframework.context.annotation.PropertySource.class)) {
			if (this.environment instanceof ConfigurableEnvironment) {
				processPropertySource(propertySource);
			}
			else {
				logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata().getClassName() +
						"]. Reason: Environment must implement ConfigurableEnvironment");
			}
		}// forcus skip

		// Process any @ComponentScan annotations
		// forcus -3 处理 @ComponentScan 注解
		/*
			@ComponentScan 介绍:
			 自动扫描指定包及其子包下的组件类（带有 @Component、@Service、@Repository、@Controller, @Configuration , 等注解的类），并将它们注册为 Spring Bean。

		 */
		/*
			使用方式:
				@ComponentScan(
					basePackages = {"com.example.service", "com.example.dao"},  // 指定扫描的包
					basePackageClasses = {MyService.class},  // 通过类指定包（类型安全）

					includeFilters = @Filter(  // 包含过滤器
					type = FilterType.ANNOTATION,
					classes = MyCustomAnnotation.class
					),

					excludeFilters = @Filter(  // 排除过滤器
					type = FilterType.ASSIGNABLE_TYPE,
					classes = ExcludedClass.class
					),
					useDefaultFilters = true,  // 是否使用默认过滤器（@Component等）

					nameGenerator = MyBeanNameGenerator.class,  // 自定义Bean名称生成器

					scopeResolver = MyScopeMetadataResolver.class,  // 自定义作用域解析器

					lazyInit = true  // 是否延迟初始化
				)
		 */
		Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(
				sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
		// forcus 确保有@ComponentScan注解,并且判断是否需要跳过，此时处于 ConfigurationPhase.REGISTER_BEAN 阶段
		if (!componentScans.isEmpty() &&
				!this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
			// 没有被跳过
			for (AnnotationAttributes componentScan : componentScans) {
				// The config class is annotated with @ComponentScan -> perform the scan immediately
				// forcus 返回扫描到的所有Bean定义 ( scannedBeanDefinitions )
				Set<BeanDefinitionHolder> scannedBeanDefinitions =
						this.componentScanParser.parse(componentScan, sourceClass.getMetadata().getClassName());
				// Check the set of scanned definitions for any further config classes and parse recursively if needed
				// forcus 递归处理所有扫描到的Bean定义
				for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
					BeanDefinition bdCand = holder.getBeanDefinition().getOriginatingBeanDefinition(); // forcus 获取原始BeanDefinition（处理代理情况）
					if (bdCand == null) {
						bdCand = holder.getBeanDefinition(); // 如果没有原始定义，使用当前定义
					}
					// forcus 检查是否为配置类候选者,如果是,那么递归解析
					/*
							@Configuration
							@ComponentScan("com.example.config")  // 扫描包中可能有其他@Configuration类
							public class AppConfig {
								// 扫描到的其他配置类会被递归处理
							}
					 */
					if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
						parse(bdCand.getBeanClassName(), holder.getBeanName());
					}
				}
			}
		}

		// Process any @Import annotations
		// forcus -4 处理 @Import 注解
		/*
			在Spring中,支持三种方式导入配置类:
				- @Import(DataSourceConfig.class):导入普通配置类(这个配置类就是单纯的被@Configuration标注了)
				- @Import(MyImportSelector.class):导入 ImportSelector 实现类(该类实现了ImportSelector接口)
				- @Import(MyRegistrar.class)：导入 ImportBeanDefinitionRegistrar 实现类
		    forcus ImportSelector & ImportBeanDefinitionRegistrar 介绍
		    forcus 除了这两个接口外,还有另外一个 ： DeferredImportSelector(ImportSelector的子类)，这是springboot的核心
		    ---> 可以跳转到 md/Import接口详解.md 中查看相关介绍 ～
		 */
		/*
			getImports(sourceClass)：获取当前配置类上的所有@Import()注解,及其参数(也就是value值,这通常是xxx.class)
				- 比如下面这种情况,那么在这里 getImports(sourceClass) 会获取到2个class {DataConfig.class && AutoConfigurationImportSelector.class}
					@Target(ElementType.TYPE)
					@Retention(RetentionPolicy.RUNTIME)
					@Import(AutoConfigurationImportSelector.class)  // 元注解中的@Import
					public @interface EnableAutoConfiguration {
					}

					@Configuration
					@EnableAutoConfiguration  // 会递归收集到AutoConfigurationImportSelector
					@Import({ DataConfig.class })  // 直接的@Import
					public class AppConfig {
					}
		     processImports() - 然后就是进去处理
		 */
		/*
			处理当前配置类上的@Import注解中的类(@Import(xxx.class))
			在这里处理的是 xxx.class 类，会根据不同类型的进行不同的处理
			 type-1:  xxx.class 是 ImportSelector 类型的
			 	- 如果是 DeferredImportSelector类型的，则进行延迟处理
			 	- 否则是纯种的 ImportSelector类型，那么立即递归处理返回的类
			 type-2:  xxx.class ImportBeanDefinitionRegistrar 类型的
			 	- 收集起来，后续统一处理
			 type-3:  xxx.class 只是一个普通的配置类(@Configuration标注的)
			 	- 作为配置类递归处理
		 */
		processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

		// Process any @ImportResource annotations
		// forcus @ImportResource 注解用于在 Java 配置类中导入 XML 配置文件，实现 Java 配置和 XML 配置的混合使用
		// 这里可以不用深入了解,因为目前几乎已经不使用xml配置了
		AnnotationAttributes importResource =
				AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
		if (importResource != null) {
			String[] resources = importResource.getStringArray("locations");
			Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
			for (String resource : resources) {
				String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
				configClass.addImportedResource(resolvedResource, readerClass);
			}
		}

		// Process individual @Bean methods
		// forcus 收集当前配置类中所有标注了@Bean注解的方法，并将它们封装成BeanMethod对象添加到配置类中
		/*
			retrieveBeanMethodMetadata(sourceClass): 获取当前 sourceClass 中所有标注了 @Bean 注解的方法 (返回的是一个set集合)
				- 内部解决了Java反射API的一个关键问题
				{
					Class.getDeclaredMethods()返回的方法顺序是不确定的
					相同的代码在不同JVM运行时，Bean的注册顺序可能不同
					可能导致依赖注入的不一致性，特别是当存在多个相同类型的Bean时
				}
		 */
		Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
		// 循环处理每个方法,将方法的元数据包装为BeanMethod对象添加到配置类中 - 添加到 configClass中
		// forcus 在后续Bean定义注册阶段使用
		/*
			这里再简单扩展一下:对BeanMethod的校验逻辑
			 1. 为什么需要对@Configuration配置类进行CGLIB校验呢?
			  - 这是为了确保@Bean的单例语义,在其他方法中可能直接通过调用方法来获取对象(这会new一个新的对象)，而不是选择去容器中获取对象
			  - 而这个问题的解决办法就是通过代理(在Spring中是通过CGLIB代理来实现的)
			 2. CGLIB的工作原理(简单介绍)
			  - 为要被代理的类「在这里是@Configuration配置类」生成一个子类
			  - 方法重写：子类重写所有的@Bean方法
			  - 拦截调用：而重写的方法不会执行原逻辑，而是先检查容器中是否已经有对应的Bean对象了，从而保证了单例语义
			 3. 限制(由于@Bean方法需要被重写)
			  - @Bean方法不能是final的 ： final方法不能被重写
			  - @Bean方法不能是private的 ： private方法不能被重写
			  - @Bean方法不能是static的 ： static方法是属于类的，不是属于对象的，无法被重写(编译期确定)
		 */
		for (MethodMetadata methodMetadata : beanMethods) {
			configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
		}

		// Process default methods on interfaces
		// forcus 处理配置类实现的接口的@Bean默认方法(接口的默认方法享受与类方法相同的CGLIB代理保护)
		processInterfaces(configClass, sourceClass);

		// Process superclass, if any
		// forcus 处理配置类的父类,在这里会返回父类的sourceClass
		if (sourceClass.getMetadata().hasSuperClass()) {
			String superclass = sourceClass.getMetadata().getSuperClassName();
			if (superclass != null && !superclass.startsWith("java") &&
					!this.knownSuperclasses.containsKey(superclass)) {
				this.knownSuperclasses.put(superclass, configClass);
				// Superclass found, return its annotation metadata and recurse
				return sourceClass.getSuperClass();
			}
		}

		// No superclass -> processing is complete
		return null;
	}

	/**
	 * Register member (nested) classes that happen to be configuration classes themselves.
	 */
	private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass,
			Predicate<String> filter) throws IOException {

		Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
		if (!memberClasses.isEmpty()) {
			List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
			for (SourceClass memberClass : memberClasses) {
				if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata()) &&
						!memberClass.getMetadata().getClassName().equals(configClass.getMetadata().getClassName())) {
					candidates.add(memberClass);
				}
			}
			OrderComparator.sort(candidates);
			for (SourceClass candidate : candidates) {
				if (this.importStack.contains(configClass)) {
					this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
				}
				else {
					this.importStack.push(configClass);
					try {
						processConfigurationClass(candidate.asConfigClass(configClass), filter);
					}
					finally {
						this.importStack.pop();
					}
				}
			}
		}
	}

	/**
	 * Register default methods on interfaces implemented by the configuration class.
	 */
	private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
		for (SourceClass ifc : sourceClass.getInterfaces()) {
			Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
			for (MethodMetadata methodMetadata : beanMethods) {
				if (!methodMetadata.isAbstract()) {
					// A default method or other concrete method on a Java 8+ interface...
					configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
				}
			}
			processInterfaces(configClass, ifc);
		}
	}

	/**
	 * Retrieve the metadata for all <code>@Bean</code> methods.
	 */
	private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
		AnnotationMetadata original = sourceClass.getMetadata();
		Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
		if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
			// Try reading the class file via ASM for deterministic declaration order...
			// Unfortunately, the JVM's standard reflection returns methods in arbitrary
			// order, even between different runs of the same application on the same JVM.
			try {
				AnnotationMetadata asm =
						this.metadataReaderFactory.getMetadataReader(original.getClassName()).getAnnotationMetadata();
				Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
				if (asmMethods.size() >= beanMethods.size()) {
					Set<MethodMetadata> candidateMethods = new LinkedHashSet<>(beanMethods);
					Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
					for (MethodMetadata asmMethod : asmMethods) {
						for (Iterator<MethodMetadata> it = candidateMethods.iterator(); it.hasNext();) {
							MethodMetadata beanMethod = it.next();
							if (beanMethod.getMethodName().equals(asmMethod.getMethodName())) {
								selectedMethods.add(beanMethod);
								it.remove();
								break;
							}
						}
					}
					if (selectedMethods.size() == beanMethods.size()) {
						// All reflection-detected methods found in ASM method set -> proceed
						beanMethods = selectedMethods;
					}
				}
			}
			catch (IOException ex) {
				logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
				// No worries, let's continue with the reflection metadata we started with...
			}
		}
		return beanMethods;
	}


	/**
	 * Process the given <code>@PropertySource</code> annotation metadata.
	 * @param propertySource metadata for the <code>@PropertySource</code> annotation found
	 * @throws IOException if loading a property source failed
	 */
	private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
		String name = propertySource.getString("name");
		if (!StringUtils.hasLength(name)) {
			name = null;
		}
		String encoding = propertySource.getString("encoding");
		if (!StringUtils.hasLength(encoding)) {
			encoding = null;
		}
		String[] locations = propertySource.getStringArray("value");
		Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
		boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

		Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
		PropertySourceFactory factory = (factoryClass == PropertySourceFactory.class ?
				DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

		for (String location : locations) {
			try {
				String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
				Resource resource = this.resourceLoader.getResource(resolvedLocation);
				addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
			}
			catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
				// Placeholders not resolvable or resource not found when trying to open it
				if (ignoreResourceNotFound) {
					if (logger.isInfoEnabled()) {
						logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
					}
				}
				else {
					throw ex;
				}
			}
		}
	}

	private void addPropertySource(PropertySource<?> propertySource) {
		String name = propertySource.getName();
		MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

		if (this.propertySourceNames.contains(name)) {
			// We've already added a version, we need to extend it
			PropertySource<?> existing = propertySources.get(name);
			if (existing != null) {
				PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ?
						((ResourcePropertySource) propertySource).withResourceName() : propertySource);
				if (existing instanceof CompositePropertySource) {
					((CompositePropertySource) existing).addFirstPropertySource(newSource);
				}
				else {
					if (existing instanceof ResourcePropertySource) {
						existing = ((ResourcePropertySource) existing).withResourceName();
					}
					CompositePropertySource composite = new CompositePropertySource(name);
					composite.addPropertySource(newSource);
					composite.addPropertySource(existing);
					propertySources.replace(name, composite);
				}
				return;
			}
		}

		if (this.propertySourceNames.isEmpty()) {
			propertySources.addLast(propertySource);
		}
		else {
			String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
			propertySources.addBefore(firstProcessed, propertySource);
		}
		this.propertySourceNames.add(name);
	}


	/**
	 * Returns {@code @Import} class, considering all meta-annotations.
	 */
	private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
		Set<SourceClass> imports = new LinkedHashSet<>();
		Set<SourceClass> visited = new LinkedHashSet<>();
		collectImports(sourceClass, imports, visited);
		return imports;
	}

	/**
	 * Recursively collect all declared {@code @Import} values. Unlike most
	 * meta-annotations it is valid to have several {@code @Import}s declared with
	 * different values; the usual process of returning values from the first
	 * meta-annotation on a class is not sufficient.
	 * <p>For example, it is common for a {@code @Configuration} class to declare direct
	 * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
	 * annotation.
	 * @param sourceClass the class to search
	 * @param imports the imports collected so far
	 * @param visited used to track visited classes to prevent infinite recursion
	 * @throws IOException if there is any problem reading metadata from the named class
	 */
	private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited)
			throws IOException {

		if (visited.add(sourceClass)) {
			for (SourceClass annotation : sourceClass.getAnnotations()) {
				String annName = annotation.getMetadata().getClassName();
				if (!annName.equals(Import.class.getName())) {
					collectImports(annotation, imports, visited);
				}
			}
			imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
		}
	}

	private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass,
			Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter,
			boolean checkForCircularImports) {

		if (importCandidates.isEmpty()) {
			return;
		}
 		// 维护循环导入栈,避免循环导入,非重点
		if (checkForCircularImports && isChainedImportOnStack(configClass)) {
			this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
		}
		/*
		 	xxx.class - 三种导入类型的处理
		 	 - ImportSelector
		 	   - DeferredImportSelector
			 - ImportBeanDefinitionRegistrar
			 - @Configuration(普通配置类)\
			 ===>
			 在下面的for()循环中,把代码缩起来,就能看到3种不同的处理逻辑
		 */
		else {
			this.importStack.push(configClass);
			try {
				for (SourceClass candidate : importCandidates) { // forcus processImports就是 配置类上所有的@Import(xx.class)导入的类,在这里循环处理
					// forcus 处理 ImportSelector
					if (candidate.isAssignable(ImportSelector.class)) {
						// Candidate class is an ImportSelector -> delegate to it to determine imports
						// 加载class并且实例化 xxx.class -> ImportSelector selector
						Class<?> candidateClass = candidate.loadClass();
						ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class,
								this.environment, this.resourceLoader, this.registry);
						// 处理 ImportSelector的过滤器
						/*
							过滤器的作用: 排除不需要处理的类,后续处理中,所有类名都会结果这个 exclusionFilter 检查
							Spring中默认的过滤器：DEFAULT_EXCLUSION_FILTER
							{ 排除 Java 核心注解类（如 @Retention、@Target 等 / 排除 Spring 的基础注解类 }
								Predicate<String> DEFAULT_EXCLUSION_FILTER = className ->
											(className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));
						 */
						Predicate<String> selectorFilter = selector.getExclusionFilter();
						if (selectorFilter != null) {
							exclusionFilter = exclusionFilter.or(selectorFilter); // forcus 合并过滤器,任何一个过滤器返回true,那么当前类就会被排除
						}
						// forcus 处理 DeferredImportSelector
						if (selector instanceof DeferredImportSelector) {
							// forcus 委托给 deferredImportSelectorHandler 来处理 (通常只是添加到集合中)
							this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
						}
						// 否则,是"纯种"的 ImportSelector
						else {
							// forcus 调用 ImportSelector.selectImports() 方法,获取导入的类名
							String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
							Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
							// forcus 对返回的类名,递归调用 processImports() 方法 {ps: 这里的类名,是通过Import(xxx.class)导入的xxx.class},而前面说过,@Import(xxx.class)导入的类有三种类型
							processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
						}
					}
					// forcus 处理 ImportBeanDefinitionRegistrar
					else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
						// Candidate class is an ImportBeanDefinitionRegistrar ->
						// delegate to it to register additional bean definitions
						// 加载类并且实例化 xxx.class -> ImportBeanDefinitionRegistrar registrar
						Class<?> candidateClass = candidate.loadClass();
						ImportBeanDefinitionRegistrar registrar =
								ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class,
										this.environment, this.resourceLoader, this.registry);
						// forcus 这里也是同样,是不会立即执行的,而是添加到配置类中
						/*
							forcus 注意这里与 DeferredImportSelector 的区别
							 - DeferredImportSelector 是添加到 deferredImportSelectorHandler 中
							 - ImportBeanDefinitionRegistrar 是添加到配置类(ConfigurationClass)中的 importBeanDefinitionRegistrars 属性
							   - 实际的执行会交给后续的 ConfigurationClassBeanDefinitionReader 执行 (Bean注册阶段执行)
						 */
						configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
					}
					// forcus 处理 @Configuration(普通配置类)
					else {
						// Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
						// process it as an @Configuration class
						this.importStack.registerImport(
								currentSourceClass.getMetadata(), candidate.getMetadata().getClassName());
						// forcus 调用 processConfigurationClass 来解析配置类 - 复用的方法
						processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
					}
				}
			}
			catch (BeanDefinitionStoreException ex) {
				throw ex;
			}
			catch (Throwable ex) {
				throw new BeanDefinitionStoreException(
						"Failed to process import candidates for configuration class [" +
						configClass.getMetadata().getClassName() + "]", ex);
			}
			finally {
				this.importStack.pop();
			}
		}
	}

	private boolean isChainedImportOnStack(ConfigurationClass configClass) {
		if (this.importStack.contains(configClass)) {
			String configClassName = configClass.getMetadata().getClassName();
			AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
			while (importingClass != null) {
				if (configClassName.equals(importingClass.getClassName())) {
					return true;
				}
				importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
			}
		}
		return false;
	}

	ImportRegistry getImportRegistry() {
		return this.importStack;
	}


	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
	 */
	private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
		AnnotationMetadata metadata = configurationClass.getMetadata();
		if (metadata instanceof StandardAnnotationMetadata) {
			return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
		}
		return asSourceClass(metadata.getClassName(), filter);
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a {@link Class}.
	 */
	SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
		if (classType == null || filter.test(classType.getName())) {
			return this.objectSourceClass;
		}
		try {
			// Sanity test that we can reflectively read annotations,
			// including Class attributes; if not -> fall back to ASM
			for (Annotation ann : classType.getDeclaredAnnotations()) {
				AnnotationUtils.validateAnnotation(ann);
			}
			return new SourceClass(classType);
		}
		catch (Throwable ex) {
			// Enforce ASM via class name resolution
			return asSourceClass(classType.getName(), filter);
		}
	}

	/**
	 * Factory method to obtain a {@link SourceClass} collection from class names.
	 */
	private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
		List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
		for (String className : classNames) {
			annotatedClasses.add(asSourceClass(className, filter));
		}
		return annotatedClasses;
	}

	/**
	 * Factory method to obtain a {@link SourceClass} from a class name.
	 */
	@SuppressWarnings("deprecation")
	SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
		if (className == null || filter.test(className)) {
			return this.objectSourceClass;
		}
		if (className.startsWith("java")) {
			// Never use ASM for core java types
			try {
				return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new org.springframework.core.NestedIOException("Failed to load class [" + className + "]", ex);
			}
		}
		return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
	}


	@SuppressWarnings("serial")
	private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

		private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

		public void registerImport(AnnotationMetadata importingClass, String importedClass) {
			this.imports.add(importedClass, importingClass);
		}

		@Override
		@Nullable
		public AnnotationMetadata getImportingClassFor(String importedClass) {
			return CollectionUtils.lastElement(this.imports.get(importedClass));
		}

		@Override
		public void removeImportingClass(String importingClass) {
			for (List<AnnotationMetadata> list : this.imports.values()) {
				for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext();) {
					if (iterator.next().getClassName().equals(importingClass)) {
						iterator.remove();
						break;
					}
				}
			}
		}

		/**
		 * Given a stack containing (in order)
		 * <ul>
		 * <li>com.acme.Foo</li>
		 * <li>com.acme.Bar</li>
		 * <li>com.acme.Baz</li>
		 * </ul>
		 * return "[Foo->Bar->Baz]".
		 */
		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner("->", "[", "]");
			for (ConfigurationClass configurationClass : this) {
				joiner.add(configurationClass.getSimpleName());
			}
			return joiner.toString();
		}
	}


	private class DeferredImportSelectorHandler {

		@Nullable
		private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

		/**
		 * Handle the specified {@link DeferredImportSelector}. If deferred import
		 * selectors are being collected, this registers this instance to the list. If
		 * they are being processed, the {@link DeferredImportSelector} is also processed
		 * immediately according to its {@link DeferredImportSelector.Group}.
		 * @param configClass the source configuration class
		 * @param importSelector the selector to handle
		 */
		public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
			// 创建一个持有者对象，包装配置类和选择器
			DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
			// 关键判断：deferredImportSelectors 是否为 null (这种情况很少见)
			if (this.deferredImportSelectors == null) {
				DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
				handler.register(holder);
				handler.processGroupImports();
			}
			else {
				this.deferredImportSelectors.add(holder); // forcus 添加到 deferredImportSelectors 中
			}
		}

		public void process() {
			List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
			this.deferredImportSelectors = null;
			try {
				if (deferredImports != null) {
					DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
					// 按优先级排序所有延迟导入选择器(支持@Order/Order接口)
					// forcus springboot中的AutoConfigurationImportSelector通常拥有最低优先级，确保在其他自动配置之后执行
					deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
					// forcus 分组注册,按Group类型分组 -- register()方法
					// 相当于处理每个deferredImports,调用 handler::register(deferredImports)
					deferredImports.forEach(handler::register);
					// forcus 这个是真正的最后处理阶段了,负责将所有分组收集的导入项转换为实际的配置类处理
					handler.processGroupImports();
				}
			}
			finally {
				this.deferredImportSelectors = new ArrayList<>();
			}
		}
	}


	private class DeferredImportSelectorGroupingHandler {
		/*
			forcus
			===
				  Map<Object, DeferredImportSelectorGrouping> groupings：分组管理器
				  作用:将所有DeferredImportSelector按照其Group类型进行分组，相同Group的选择器会被放在同一个分组中统一处理
				   	key: 分组标识符(分为两种类型)
						 1.Class<? extends Group>：自定义Group类（如AutoConfigurationGroup.class）
						 2.DeferredImportSelectorHolder：没有Group的选择器holder对象
				   	value: DeferredImportSelectorGrouping对象，包含该分组的所有选择器
			 ===
		 */
		private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();
		/*
			forcus
			===
				  Map<AnnotationMetadata, ConfigurationClass> configurationClasses ：配置类映射表
				  {
				  	Key：AnnotationMetadata（配置类的注解元数据）
				  	value:Value：ConfigurationClass（配置类对象）
				  }
			===
		 */
		private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

		public void register(DeferredImportSelectorHolder deferredImport) {
			// forcus 调用具体getImportGroup() 方法来获取相应的分组
			/*
				这里会出现两种情况:
				 1. 没有重写 DeferredImportSelector.getImportGroup()方法，那么使用默认分组 (DefaultDeferredImportSelectorGroup)
				 2. 重写了,并且使用了自定义分组(比如返回的是 xxxGroup.class --> 这是 DeferredImportSelector.Group 类型的，内部有一个核心方法(process(xxx)))
				 也即可能返回 null / xxx.class
			 */
			Class<? extends Group> group = deferredImport.getImportSelector().getImportGroup();
			/*
			    反感lambda表达式
			     1. Map.computeIfAbsent(K key, Function<K, V> mappingFunction)
			     { ==>
						V computeIfAbsent(K key, Function<K, V> mappingFunction) {
							V value = map.get(key);
							if (value == null) {
								value = mappingFunction.apply(key);  // 计算新值
								map.put(key, value);                 // 存入map
							}
							return value;
						}
			     }

			    K key: group != null ? group : deferredImport
			     - 如果group不为null,则使用group作为key -- MyCustomGroup.class
				 - 如果group为null,则使用deferredImport作为key (DeferredImportSelectorHolder类型的)

				Function<K, V> mappingFunction
				forcus 这里的逻辑是什么呢？
				 1. 如果某个 DeferredImportSelector 有自己的分组，那么在这里会创建一个新的 DeferredImportSelectorGrouping 对象
				  	只有第一个会创建，后续的都是直接返回之前创建的DeferredImportSelectorGrouping对象(前提是属于相同的分组)
				 2. 不同的 DeferredImportSelector 如果有不同的分组，那么对应的 DeferredImportSelectorGrouping对象是不同的
				 3. 没有重写对应的方法的，那么 每个 DeferredImportSelector 都会创建一个新的 DeferredImportSelectorGrouping对象

				 ===>
				 这里还有3个属性需要关注一下：
				  ===
				  DeferredImportSelectorGrouping grouping： 单个分组对象
					{
						内部的属性:
						private final DeferredImportSelector.Group group;    // Group处理器实例
						private final List<DeferredImportSelectorHolder> deferredImports;   // 该分组的所有选择器
					}
				  ===

				  ===
				  Map<Object, DeferredImportSelectorGrouping> groupings：分组管理器
				  作用:将所有DeferredImportSelector按照其Group类型进行分组，相同Group的选择器会被放在同一个分组中统一处理
				   	key: 分组标识符(分为两种类型)
						 1.Class<? extends Group>：自定义Group类（如AutoConfigurationGroup.class）
						 2.DeferredImportSelectorHolder：没有Group的选择器holder对象
				   	value: DeferredImportSelectorGrouping对象，包含该分组的所有选择器
				  ===

				  ===
				  Map<AnnotationMetadata, ConfigurationClass> configurationClasses ：配置类映射表
				  {
				  	Key：AnnotationMetadata（配置类的注解元数据）
				  	value:Value：ConfigurationClass（配置类对象）
				  }
				  ===

				  在真正处理时,还会涉及到一个对象,那就是 DeferredImportSelector.Group.Entry
				  {
						class Entry {
							private final AnnotationMetadata metadata;      // 导入方的配置类元数据(这个 DeferredImportSelector 是被哪个配置类导入的)
							private final String importClassName;           // 要导入的类名(是导入的配置类的类名，不是DeferredImportSelector的类名)
							// 一个 DeferredImportSelector 可能生成多个entry，因为可以一次性导入多个配置类
						}
				  }
			 */
			/*
				forcus groupings 例子
				groupings = {
					// 自定义分组：Spring Boot自动配置
					AutoConfigurationGroup.class -> DeferredImportSelectorGrouping {
						group: AutoConfigurationGroup实例,
						deferredImports: [AutoConfigurationImportSelector的holder, 其他自动配置选择器的holder]
					},

					// 自定义分组：用户自定义
					MyCustomGroup.class -> DeferredImportSelectorGrouping {
						group: MyCustomGroup实例,
						deferredImports: [MySelector1的holder, MySelector2的holder]
					},

					// 默认分组：独立选择器1
					DeferredImportSelectorHolder@123 -> DeferredImportSelectorGrouping {
						group: DefaultDeferredImportSelectorGroup实例,
						deferredImports: [该选择器的holder]
					},

					// 默认分组：独立选择器2
					DeferredImportSelectorHolder@456 -> DeferredImportSelectorGrouping {
						group: DefaultDeferredImportSelectorGroup实例,
						deferredImports: [该选择器的holder]
					}
				}
			 */
			DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent(
					(group != null ? group : deferredImport),
					key -> new DeferredImportSelectorGrouping(createGroup(group)));
			grouping.add(deferredImport);
			this.configurationClasses.put(deferredImport.getConfigurationClass().getMetadata(),
					deferredImport.getConfigurationClass());
		}
		// forcus 真正最后处理 DeferredImportSelector 的方法
		public void processGroupImports() {
			// 还记得这个groupings吗?
		/*
			forcus
			===
				  Map<Object, DeferredImportSelectorGrouping> groupings：分组管理器
				  作用:将所有DeferredImportSelector按照其Group类型进行分组，相同Group的选择器会被放在同一个分组中统一处理
				   	key: 分组标识符(分为两种类型)
						 1.Class<? extends Group>：自定义Group类（如AutoConfigurationGroup.class）
						 2.DeferredImportSelectorHolder：没有Group的选择器holder对象
				   	value: DeferredImportSelectorGrouping对象，包含该分组的所有选择器
			 ===
		 */
			// 依次处理所有的 DeferredImportSelectorGrouping 分组对象
			// 每个分组对象可能包含多个 DeferredImportSelector对象
			for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
				// 获取候选过滤器(获取基础过滤器和Selector过滤器)
				// 基础过滤器: DEFAULT_EXCLUSION_FILTER-> 排除java基础类和spring注解类
				// Selector过滤器: 每个DeferredImportSelector对象可以提供自己的排除规则
				Predicate<String> exclusionFilter = grouping.getCandidateFilter();
				/*
					forcus
					  1. grouping.getImports()：这里就是前面说的,entry的创建过程
						 以下面为例子:
						 那么在这里 grouping.getImports() 返回的就是:
						 [
								Entry{metadata=Application_metadata, importClassName=DatabaseAutoConfiguration.class},
								Entry{metadata=Application_metadata, importClassName=CacheAutoConfiguration.class}
						 ]
						@Configuration
						@Import(MyAutoConfigurationImportSelector.class)
						public class Application {
						}

						public class MyAutoConfigurationImportSelector implements DeferredImportSelector {
							@Override
							public String[] selectImports(AnnotationMetadata metadata) {
								return new String[]{
									"com.example.DatabaseAutoConfiguration",
									"com.example.CacheAutoConfiguration"
								};
							}
						}  
					forcus 
					然后就是针对每一个entry进行处理
						1. ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
						   -- 获取entry中的配置类(对应上面就是CacheAutoConfiguration.class)
						2.递归调用processImports()
				*/
				
				grouping.getImports().forEach(entry -> {
					ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
					try {
						processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter),
								Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)),
								exclusionFilter, false);
					}
					catch (BeanDefinitionStoreException ex) {
						throw ex;
					}
					catch (Throwable ex) {
						throw new BeanDefinitionStoreException(
								"Failed to process import candidates for configuration class [" +
										configurationClass.getMetadata().getClassName() + "]", ex);
					}
				});
			}
		}

		private Group createGroup(@Nullable Class<? extends Group> type) {
			Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
			return ParserStrategyUtils.instantiateClass(effectiveType, Group.class,
					ConfigurationClassParser.this.environment,
					ConfigurationClassParser.this.resourceLoader,
					ConfigurationClassParser.this.registry);
		}
	}


	private static class DeferredImportSelectorHolder {

		private final ConfigurationClass configurationClass;

		private final DeferredImportSelector importSelector;

		public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
			this.configurationClass = configClass;
			this.importSelector = selector;
		}

		public ConfigurationClass getConfigurationClass() {
			return this.configurationClass;
		}

		public DeferredImportSelector getImportSelector() {
			return this.importSelector;
		}
	}


	private static class DeferredImportSelectorGrouping {

		private final DeferredImportSelector.Group group;

		private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

		DeferredImportSelectorGrouping(Group group) {
			this.group = group;
		}

		public void add(DeferredImportSelectorHolder deferredImport) {
			this.deferredImports.add(deferredImport);
		}

		/**
		 * Return the imports defined by the group.
		 * @return each import with its associated configuration class
		 */
		public Iterable<Group.Entry> getImports() {
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				this.group.process(deferredImport.getConfigurationClass().getMetadata(),
						deferredImport.getImportSelector());
			}
			return this.group.selectImports();
		}

		public Predicate<String> getCandidateFilter() {
			Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
			for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
				Predicate<String> selectorFilter = deferredImport.getImportSelector().getExclusionFilter();
				if (selectorFilter != null) {
					mergedFilter = mergedFilter.or(selectorFilter);
				}
			}
			return mergedFilter;
		}
	}


	private static class DefaultDeferredImportSelectorGroup implements Group {

		private final List<Entry> imports = new ArrayList<>();

		@Override
		public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
			for (String importClassName : selector.selectImports(metadata)) {
				this.imports.add(new Entry(metadata, importClassName));
			}
		}

		@Override
		public Iterable<Entry> selectImports() {
			return this.imports;
		}
	}


	/**
	 * Simple wrapper that allows annotated source classes to be dealt with
	 * in a uniform manner, regardless of how they are loaded.
	 */
	private class SourceClass implements Ordered {

		private final Object source;  // Class or MetadataReader

		private final AnnotationMetadata metadata;

		public SourceClass(Object source) {
			this.source = source;
			if (source instanceof Class) {
				this.metadata = AnnotationMetadata.introspect((Class<?>) source);
			}
			else {
				this.metadata = ((MetadataReader) source).getAnnotationMetadata();
			}
		}

		public final AnnotationMetadata getMetadata() {
			return this.metadata;
		}

		@Override
		public int getOrder() {
			Integer order = ConfigurationClassUtils.getOrder(this.metadata);
			return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
		}

		public Class<?> loadClass() throws ClassNotFoundException {
			if (this.source instanceof Class) {
				return (Class<?>) this.source;
			}
			String className = ((MetadataReader) this.source).getClassMetadata().getClassName();
			return ClassUtils.forName(className, resourceLoader.getClassLoader());
		}

		public boolean isAssignable(Class<?> clazz) throws IOException {
			if (this.source instanceof Class) {
				return clazz.isAssignableFrom((Class<?>) this.source);
			}
			return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
		}

		public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
			if (this.source instanceof Class) {
				return new ConfigurationClass((Class<?>) this.source, importedBy);
			}
			return new ConfigurationClass((MetadataReader) this.source, importedBy);
		}

		public Collection<SourceClass> getMemberClasses() throws IOException {
			Object sourceToProcess = this.source;
			if (sourceToProcess instanceof Class) {
				Class<?> sourceClass = (Class<?>) sourceToProcess;
				try {
					Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
					List<SourceClass> members = new ArrayList<>(declaredClasses.length);
					for (Class<?> declaredClass : declaredClasses) {
						members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
					}
					return members;
				}
				catch (NoClassDefFoundError err) {
					// getDeclaredClasses() failed because of non-resolvable dependencies
					// -> fall back to ASM below
					sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
				}
			}

			// ASM-based resolution - safe for non-resolvable classes as well
			MetadataReader sourceReader = (MetadataReader) sourceToProcess;
			String[] memberClassNames = sourceReader.getClassMetadata().getMemberClassNames();
			List<SourceClass> members = new ArrayList<>(memberClassNames.length);
			for (String memberClassName : memberClassNames) {
				try {
					members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
				}
				catch (IOException ex) {
					// Let's skip it if it's not resolvable - we're just looking for candidates
					if (logger.isDebugEnabled()) {
						logger.debug("Failed to resolve member class [" + memberClassName +
								"] - not considering it as a configuration class candidate");
					}
				}
			}
			return members;
		}

		public SourceClass getSuperClass() throws IOException {
			if (this.source instanceof Class) {
				return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
			}
			return asSourceClass(
					((MetadataReader) this.source).getClassMetadata().getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
		}

		public Set<SourceClass> getInterfaces() throws IOException {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Class<?> ifcClass : sourceClass.getInterfaces()) {
					result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
				}
			}
			else {
				for (String className : this.metadata.getInterfaceNames()) {
					result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
				}
			}
			return result;
		}

		public Set<SourceClass> getAnnotations() {
			Set<SourceClass> result = new LinkedHashSet<>();
			if (this.source instanceof Class) {
				Class<?> sourceClass = (Class<?>) this.source;
				for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
					Class<?> annType = ann.annotationType();
					if (!annType.getName().startsWith("java")) {
						try {
							result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			else {
				for (String className : this.metadata.getAnnotationTypes()) {
					if (!className.startsWith("java")) {
						try {
							result.add(getRelated(className));
						}
						catch (Throwable ex) {
							// An annotation not present on the classpath is being ignored
							// by the JVM's class loading -> ignore here as well.
						}
					}
				}
			}
			return result;
		}

		public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
			Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
			if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
				return Collections.emptySet();
			}
			String[] classNames = (String[]) annotationAttributes.get(attribute);
			Set<SourceClass> result = new LinkedHashSet<>();
			for (String className : classNames) {
				result.add(getRelated(className));
			}
			return result;
		}

		@SuppressWarnings("deprecation")
		private SourceClass getRelated(String className) throws IOException {
			if (this.source instanceof Class) {
				try {
					Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
					return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
				}
				catch (ClassNotFoundException ex) {
					// Ignore -> fall back to ASM next, except for core java types.
					if (className.startsWith("java")) {
						throw new org.springframework.core.NestedIOException("Failed to load class [" + className + "]", ex);
					}
					return new SourceClass(metadataReaderFactory.getMetadataReader(className));
				}
			}
			return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			return (this == other || (other instanceof SourceClass &&
					this.metadata.getClassName().equals(((SourceClass) other).metadata.getClassName())));
		}

		@Override
		public int hashCode() {
			return this.metadata.getClassName().hashCode();
		}

		@Override
		public String toString() {
			return this.metadata.getClassName();
		}
	}


	/**
	 * {@link Problem} registered upon detection of a circular {@link Import}.
	 */
	private static class CircularImportProblem extends Problem {

		public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
			super(String.format("A circular @Import has been detected: " +
					"Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is " +
					"already present in the current import stack %s", importStack.element().getSimpleName(),
					attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack),
					new Location(importStack.element().getResource(), attemptedImport.getMetadata()));
		}
	}

}
