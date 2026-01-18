package com.debug.demo;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.*;
import java.util.Map;

/**
 * ImportBeanDefinitionRegistrar接口演示
 * 
 * ImportBeanDefinitionRegistrar允许直接向容器注册BeanDefinition
 * 提供了最大的灵活性，可以完全控制Bean的注册过程
 */
public class ImportBeanDefinitionRegistrarDemo {

    public static void main(String[] args) {
        System.out.println("=== ImportBeanDefinitionRegistrar Demo ===");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        
        // 查看容器中的Bean
        System.out.println("\n注册的Bean:");
        String[] beanNames = context.getBeanDefinitionNames();
        java.util.Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            if (!beanName.startsWith("org.springframework")) {
                Object bean = context.getBean(beanName);
                System.out.println("  - " + beanName + " : " + bean.getClass().getSimpleName() + " -> " + bean);
            }
        }
        
        // 测试动态注册的服务
        System.out.println("\n测试动态注册的服务:");
        try {
            UserService userService = context.getBean("userService", UserService.class);
            System.out.println("UserService: " + userService.findUser("123"));
            
            OrderService orderService = context.getBean("orderService", OrderService.class);
            System.out.println("OrderService: " + orderService.findOrder("456"));
            
            ProductService productService = context.getBean("productService", ProductService.class);
            System.out.println("ProductService: " + productService.findProduct("789"));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        context.close();
    }

    /**
     * 主配置类，使用@EnableDynamicServices启用动态服务注册
     */
    @Configuration
    @EnableDynamicServices(
        services = {"user", "order", "product"},
        prefix = "dynamic"
    )
    static class AppConfig {
        
        @Bean
        public String appInfo() {
            return "ImportBeanDefinitionRegistrar Demo Application";
        }
    }

    /**
     * 启用动态服务的注解
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Import(DynamicServiceRegistrar.class)
    @interface EnableDynamicServices {
        /**
         * 要注册的服务名称数组
         */
        String[] services() default {};
        
        /**
         * Bean名称前缀
         */
        String prefix() default "";
        
        /**
         * 是否启用缓存
         */
        boolean enableCache() default true;
    }

    /**
     * 动态服务注册器
     * 实现ImportBeanDefinitionRegistrar接口，动态注册服务Bean
     */
    static class DynamicServiceRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, 
                                          BeanDefinitionRegistry registry, 
                                          BeanNameGenerator importBeanNameGenerator) {
            
            System.out.println("\n=== DynamicServiceRegistrar.registerBeanDefinitions() 被调用 ===");
            System.out.println("导入配置类: " + importingClassMetadata.getClassName());
            
            // 获取@EnableDynamicServices注解的属性
            Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableDynamicServices.class.getName());
            
            if (attributes == null) {
                System.out.println("未找到@EnableDynamicServices注解");
                return;
            }
            
            String[] services = (String[]) attributes.get("services");
            String prefix = (String) attributes.get("prefix");
            boolean enableCache = (Boolean) attributes.get("enableCache");
            
            System.out.println("服务列表: " + java.util.Arrays.toString(services));
            System.out.println("前缀: " + prefix);
            System.out.println("启用缓存: " + enableCache);
            
            // 为每个服务注册对应的Bean
            for (String serviceName : services) {
                registerServiceBean(registry, serviceName, prefix, enableCache);
            }
            
            // 如果启用缓存，注册缓存管理器
            if (enableCache) {
                registerCacheManager(registry);
            }
            
            // 注册服务工厂
            registerServiceFactory(registry, services);
        }

        /**
         * 注册服务Bean
         */
        private void registerServiceBean(BeanDefinitionRegistry registry, String serviceName, 
                                       String prefix, boolean enableCache) {
            
            // 确定Bean名称
            String beanName = (prefix.isEmpty() ? "" : prefix + ".") + serviceName + "Service";
            
            // 根据服务名称选择实现类
            Class<?> serviceClass;
            switch (serviceName.toLowerCase()) {
                case "user":
                    serviceClass = UserService.class;
                    break;
                case "order":
                    serviceClass = OrderService.class;
                    break;
                case "product":
                    serviceClass = ProductService.class;
                    break;
                default:
                    serviceClass = GenericService.class;
            }
            
            // 创建BeanDefinition
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(serviceClass);
            
            // 设置构造函数参数
            builder.addConstructorArgValue(serviceName);
            builder.addConstructorArgValue(enableCache);
            
            // 设置Bean属性
            builder.addPropertyValue("serviceName", serviceName);
            
            // 设置作用域
            builder.setScope(BeanDefinition.SCOPE_SINGLETON);
            
            // 设置懒加载
            builder.setLazyInit(false);
            
            BeanDefinition beanDefinition = builder.getBeanDefinition();
            
            // 注册Bean
            registry.registerBeanDefinition(beanName, beanDefinition);
            
            System.out.println("注册服务Bean: " + beanName + " -> " + serviceClass.getSimpleName());
        }

        /**
         * 注册缓存管理器
         */
        private void registerCacheManager(BeanDefinitionRegistry registry) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(CacheManager.class);
            builder.addConstructorArgValue("Redis");
            
            BeanDefinition beanDefinition = builder.getBeanDefinition();
            registry.registerBeanDefinition("cacheManager", beanDefinition);
            
            System.out.println("注册缓存管理器: cacheManager -> CacheManager");
        }

        /**
         * 注册服务工厂
         */
        private void registerServiceFactory(BeanDefinitionRegistry registry, String[] services) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ServiceFactory.class);
            builder.addConstructorArgValue(services);
            
            BeanDefinition beanDefinition = builder.getBeanDefinition();
            registry.registerBeanDefinition("serviceFactory", beanDefinition);
            
            System.out.println("注册服务工厂: serviceFactory -> ServiceFactory");
        }
    }

    /**
     * 用户服务
     */
    static class UserService {
        private final String serviceName;
        private final boolean cacheEnabled;
        
        public UserService(String serviceName, boolean cacheEnabled) {
            this.serviceName = serviceName;
            this.cacheEnabled = cacheEnabled;
            System.out.println("创建UserService: " + serviceName + ", 缓存: " + cacheEnabled);
        }
        
        public String findUser(String id) {
            return "User[" + id + "] from " + serviceName + " service" + 
                   (cacheEnabled ? " (cached)" : "");
        }
        
        public void setServiceName(String serviceName) {
            // Property injection
        }
    }

    /**
     * 订单服务
     */
    static class OrderService {
        private final String serviceName;
        private final boolean cacheEnabled;
        
        public OrderService(String serviceName, boolean cacheEnabled) {
            this.serviceName = serviceName;
            this.cacheEnabled = cacheEnabled;
            System.out.println("创建OrderService: " + serviceName + ", 缓存: " + cacheEnabled);
        }
        
        public String findOrder(String id) {
            return "Order[" + id + "] from " + serviceName + " service" + 
                   (cacheEnabled ? " (cached)" : "");
        }
        
        public void setServiceName(String serviceName) {
            // Property injection
        }
    }

    /**
     * 产品服务
     */
    static class ProductService {
        private final String serviceName;
        private final boolean cacheEnabled;
        
        public ProductService(String serviceName, boolean cacheEnabled) {
            this.serviceName = serviceName;
            this.cacheEnabled = cacheEnabled;
            System.out.println("创建ProductService: " + serviceName + ", 缓存: " + cacheEnabled);
        }
        
        public String findProduct(String id) {
            return "Product[" + id + "] from " + serviceName + " service" + 
                   (cacheEnabled ? " (cached)" : "");
        }
        
        public void setServiceName(String serviceName) {
            // Property injection
        }
    }

    /**
     * 通用服务
     */
    static class GenericService {
        private final String serviceName;
        private final boolean cacheEnabled;
        
        public GenericService(String serviceName, boolean cacheEnabled) {
            this.serviceName = serviceName;
            this.cacheEnabled = cacheEnabled;
            System.out.println("创建GenericService: " + serviceName + ", 缓存: " + cacheEnabled);
        }
        
        public String process(String data) {
            return "Processed[" + data + "] by " + serviceName + " service" + 
                   (cacheEnabled ? " (cached)" : "");
        }
        
        public void setServiceName(String serviceName) {
            // Property injection
        }
    }

    /**
     * 缓存管理器
     */
    static class CacheManager {
        private final String cacheType;
        
        public CacheManager(String cacheType) {
            this.cacheType = cacheType;
            System.out.println("创建CacheManager: " + cacheType);
        }
        
        @Override
        public String toString() {
            return "CacheManager{type='" + cacheType + "'}";
        }
    }

    /**
     * 服务工厂
     */
    static class ServiceFactory {
        private final String[] supportedServices;
        
        public ServiceFactory(String[] supportedServices) {
            this.supportedServices = supportedServices;
            System.out.println("创建ServiceFactory，支持的服务: " + java.util.Arrays.toString(supportedServices));
        }
        
        @Override
        public String toString() {
            return "ServiceFactory{services=" + java.util.Arrays.toString(supportedServices) + "}";
        }
    }
}