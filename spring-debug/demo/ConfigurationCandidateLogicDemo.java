package demo;

import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * 配置候选者逻辑演示
 * 
 * 展示配置注解和@Bean方法的关系：是"或"的关系，不是"与"的关系
 */
public class ConfigurationCandidateLogicDemo {

    public static void main(String[] args) {
        System.out.println("=== 配置候选者判断逻辑演示 ===\n");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        
        // 注册不同类型的配置类
        context.register(
            OnlyAnnotationConfig.class,      // 只有配置注解
            OnlyBeanMethodConfig.class,      // 只有@Bean方法
            BothConfig.class,                // 两者都有
            NeitherConfig.class              // 两者都没有
        );
        
        context.refresh();
        
        // 检查注册结果
        System.out.println("=== Bean注册结果 ===");
        checkBean(context, "onlyAnnotationBean");
        checkBean(context, "onlyBeanMethodBean");
        checkBean(context, "bothConfigBean");
        checkBean(context, "neitherConfigBean");
        
        context.close();
    }
    
    private static void checkBean(AnnotationConfigApplicationContext context, String beanName) {
        try {
            Object bean = context.getBean(beanName);
            System.out.println("✅ " + beanName + ": " + bean.getClass().getSimpleName());
        } catch (Exception e) {
            System.out.println("❌ " + beanName + ": 未注册");
        }
    }
}

/**
 * 场景1：只有配置注解，没有@Bean方法
 * 
 * 结果：✅ 是配置候选者
 * 原因：有@Component注解
 */
@Component  // 🔥 有配置注解
class OnlyAnnotationConfig {
    // 没有@Bean方法
    
    // 这个类本身会被注册为Bean
    public String getValue() {
        return "OnlyAnnotation";
    }
}

/**
 * 场景2：只有@Bean方法，没有配置注解
 * 
 * 结果：✅ 是配置候选者
 * 原因：有@Bean方法
 */
class OnlyBeanMethodConfig {  // 🔥 没有配置注解
    
    @Bean  // 🔥 有@Bean方法
    public OnlyBeanMethodBean onlyBeanMethodBean() {
        System.out.println("🔧 创建OnlyBeanMethodBean");
        return new OnlyBeanMethodBean();
    }
    
    static class OnlyBeanMethodBean {}
}

/**
 * 场景3：既有配置注解，又有@Bean方法
 * 
 * 结果：✅ 是配置候选者
 * 原因：两个条件都满足（满足任一即可）
 */
@Configuration  // 🔥 有配置注解
class BothConfig {
    
    @Bean  // 🔥 还有@Bean方法
    public BothConfigBean bothConfigBean() {
        System.out.println("🔧 创建BothConfigBean");
        return new BothConfigBean();
    }
    
    static class BothConfigBean {}
}

/**
 * 场景4：既没有配置注解，也没有@Bean方法
 * 
 * 结果：❌ 不是配置候选者
 * 原因：两个条件都不满足
 */
class NeitherConfig {  // 🔥 没有配置注解
    // 🔥 没有@Bean方法
    
    public NeitherConfigBean neitherConfigBean() {  // 普通方法，不是@Bean
        return new NeitherConfigBean();
    }
    
    static class NeitherConfigBean {}
}

/**
 * 源码逻辑模拟
 */
class ConfigurationCandidateSimulator {
    
    /**
     * 模拟 ConfigurationClassUtils.isConfigurationCandidate() 的逻辑
     */
    public static void simulateLogic() {
        System.out.println("\n=== 源码逻辑模拟 ===");
        System.out.println("public static boolean isConfigurationCandidate(AnnotationMetadata metadata) {");
        System.out.println("    // 1️⃣ 排除接口");
        System.out.println("    if (metadata.isInterface()) {");
        System.out.println("        return false;");
        System.out.println("    }");
        System.out.println();
        System.out.println("    // 2️⃣ 检查配置注解 (@Component, @ComponentScan, @Import, @ImportResource)");
        System.out.println("    for (String indicator : candidateIndicators) {");
        System.out.println("        if (metadata.isAnnotated(indicator)) {");
        System.out.println("            return true;  // 🔥 有配置注解就返回true，不再检查@Bean方法");
        System.out.println("        }");
        System.out.println("    }");
        System.out.println();
        System.out.println("    // 3️⃣ 检查@Bean方法（只有前面没有配置注解才会执行到这里）");
        System.out.println("    return hasBeanMethods(metadata);  // 🔥 有@Bean方法就返回true");
        System.out.println("}");
        
        System.out.println("\n=== 关键理解 ===");
        System.out.println("✅ 配置注解 OR @Bean方法 → 配置候选者");
        System.out.println("❌ 既没有配置注解 AND 没有@Bean方法 → 不是配置候选者");
        System.out.println("\n这是 '或' 的关系，不是 '与' 的关系！");
    }
}

/**
 * 实际测试用例
 */
class RealWorldExamples {
    
    /**
     * Spring Boot 自动配置类的典型模式
     */
    @Configuration  // 有配置注解
    @ConditionalOnClass(name = "com.example.SomeClass")
    static class AutoConfiguration {
        
        @Bean  // 还有@Bean方法
        @ConditionalOnMissingBean
        public SomeService someService() {
            return new SomeService();
        }
        
        static class SomeService {}
    }
    
    /**
     * 纯Bean工厂类（没有配置注解）
     */
    static class BeanFactory {  // 没有配置注解
        
        @Bean  // 但有@Bean方法 → 仍然是配置候选者
        public DatabaseConnection databaseConnection() {
            return new DatabaseConnection();
        }
        
        static class DatabaseConnection {}
    }
    
    /**
     * 纯组件类（没有@Bean方法）
     */
    @Service  // 有配置注解
    static class BusinessService {  // 没有@Bean方法 → 仍然是配置候选者
        
        public void doSomething() {
            System.out.println("Doing business logic");
        }
    }
    
    /**
     * 普通工具类（两者都没有）
     */
    static class UtilityClass {  // 没有配置注解，没有@Bean方法 → 不是配置候选者
        
        public static String format(String input) {
            return input.toUpperCase();
        }
    }
}