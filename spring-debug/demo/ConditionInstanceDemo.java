package demo;

import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotatedTypeMetadata;
import java.util.*;

/**
 * Spring条件实例化机制演示
 * 
 * 展示conditions集合中存放的是实例对象，不是Class
 */
public class ConditionInstanceDemo {

    public static void main(String[] args) {
        System.out.println("=== Spring条件实例化机制演示 ===\n");
        
        // 演示1：模拟Spring的条件实例化过程
        demonstrateConditionInstantiation();
        
        // 演示2：对比Class vs Instance的区别
        demonstrateClassVsInstance();
        
        // 演示3：实际Spring容器中的使用
        demonstrateSpringContainerUsage();
    }
    
    /**
     * 演示1：模拟Spring的条件实例化过程
     */
    private static void demonstrateConditionInstantiation() {
        System.out.println("=== 演示1：Spring条件实例化过程模拟 ===");
        
        // 模拟从@Conditional注解中提取的类名
        String[] conditionClassNames = {
            "demo.DatabaseCondition",
            "demo.CacheCondition",
            "demo.ProfileCondition"
        };
        
        // 🔥 Spring的实际做法：创建实例集合
        List<Condition> conditions = new ArrayList<>();
        
        System.out.println("📋 开始实例化条件类：");
        for (String className : conditionClassNames) {
            try {
                // 1️⃣ 加载类
                Class<?> clazz = Class.forName(className);
                System.out.println("   ✅ 加载类: " + clazz.getSimpleName());
                
                // 2️⃣ 创建实例 - 🔥 关键：这里创建的是实例对象！
                Condition instance = (Condition) clazz.getDeclaredConstructor().newInstance();
                System.out.println("   🔧 创建实例: " + instance.getClass().getSimpleName() + "@" + 
                                 Integer.toHexString(instance.hashCode()));
                
                // 3️⃣ 添加到集合
                conditions.add(instance);
                
            } catch (Exception e) {
                System.out.println("   ❌ 实例化失败: " + className);
            }
        }
        
        System.out.println("\n📊 最终conditions集合内容：");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            System.out.println("   [" + i + "] " + condition.getClass().getSimpleName() + 
                             " 实例 (可直接调用matches方法)");
            
            // 🔥 证明：可以直接调用实例方法
            System.out.println("       → 调用matches(): " + 
                             condition.matches(null, null) + " (模拟调用)");
        }
        System.out.println();
    }
    
    /**
     * 演示2：对比Class vs Instance的区别
     */
    private static void demonstrateClassVsInstance() {
        System.out.println("=== 演示2：Class vs Instance 对比 ===");
        
        // 方案A：存储Class对象（不是Spring的做法）
        System.out.println("🔍 方案A：存储Class对象");
        List<Class<?>> classObjects = Arrays.asList(
            DatabaseCondition.class,
            CacheCondition.class
        );
        
        System.out.println("   存储内容: Class对象");
        for (Class<?> clazz : classObjects) {
            System.out.println("   - " + clazz.getSimpleName() + ".class");
            System.out.println("     ❌ 无法直接调用matches()方法");
            System.out.println("     ❌ 每次使用都需要实例化");
        }
        
        // 方案B：存储Instance对象（Spring的做法）
        System.out.println("\n🔥 方案B：存储Instance对象（Spring的选择）");
        List<Condition> instances = Arrays.asList(
            new DatabaseCondition(),
            new CacheCondition()
        );
        
        System.out.println("   存储内容: 实例对象");
        for (Condition instance : instances) {
            System.out.println("   - " + instance.getClass().getSimpleName() + " 实例");
            System.out.println("     ✅ 可以直接调用matches()方法");
            System.out.println("     ✅ 无需重复实例化");
            System.out.println("     ✅ 可以保持内部状态");
        }
        System.out.println();
    }
    
    /**
     * 演示3：实际Spring容器中的使用
     */
    private static void demonstrateSpringContainerUsage() {
        System.out.println("=== 演示3：Spring容器中的实际使用 ===");
        
        // 设置系统属性
        System.setProperty("database.enabled", "true");
        System.setProperty("cache.type", "redis");
        
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ConditionalConfig.class);
        context.refresh();
        
        // 检查Bean是否注册成功
        try {
            Object bean = context.getBean("conditionalBean");
            System.out.println("✅ Bean注册成功: " + bean.getClass().getSimpleName());
            System.out.println("   说明：所有条件实例的matches()方法都返回了true");
        } catch (Exception e) {
            System.out.println("❌ Bean未注册: 某个条件实例的matches()方法返回了false");
        }
        
        context.close();
        System.out.println();
    }
}

/**
 * 带条件的配置类
 */
@Configuration
@Conditional({DatabaseCondition.class, CacheCondition.class})
class ConditionalConfig {
    
    @Bean
    public ConditionalBean conditionalBean() {
        System.out.println("🔧 创建ConditionalBean - 所有条件都满足");
        return new ConditionalBean();
    }
    
    static class ConditionalBean {}
}

/**
 * 数据库条件 - 实现ConfigurationCondition
 */
class DatabaseCondition implements ConfigurationCondition {
    
    // 🔥 实例字段 - 证明这是实例对象，不是Class
    private final String conditionName = "DatabaseCondition";
    private int evaluationCount = 0;
    
    @Override
    public ConfigurationPhase getConfigurationPhase() {
        return ConfigurationPhase.PARSE_CONFIGURATION;
    }
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        evaluationCount++; // 🔥 实例状态会改变
        
        if (context == null) {
            System.out.println("     🔍 " + conditionName + " 模拟评估 (第" + evaluationCount + "次)");
            return true; // 模拟条件满足
        }
        
        String enabled = context.getEnvironment().getProperty("database.enabled");
        boolean result = "true".equals(enabled);
        
        System.out.println("     🔍 " + conditionName + " 评估: database.enabled=" + enabled + 
                          " → " + (result ? "满足" : "不满足") + " (第" + evaluationCount + "次)");
        
        return result;
    }
}

/**
 * 缓存条件 - 实现普通Condition
 */
class CacheCondition implements Condition {
    
    // 🔥 实例字段 - 证明这是实例对象
    private final String conditionName = "CacheCondition";
    private int evaluationCount = 0;
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        evaluationCount++; // 🔥 实例状态会改变
        
        if (context == null) {
            System.out.println("     🔍 " + conditionName + " 模拟评估 (第" + evaluationCount + "次)");
            return true; // 模拟条件满足
        }
        
        String cacheType = context.getEnvironment().getProperty("cache.type");
        boolean result = "redis".equals(cacheType);
        
        System.out.println("     🔍 " + conditionName + " 评估: cache.type=" + cacheType + 
                          " → " + (result ? "满足" : "不满足") + " (第" + evaluationCount + "次)");
        
        return result;
    }
}

/**
 * 环境条件 - 演示实例状态
 */
class ProfileCondition implements Condition {
    
    // 🔥 实例字段 - 每个实例都有独立的状态
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private int evaluationCount = 0;
    
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        evaluationCount++;
        
        System.out.println("     🔍 ProfileCondition[" + instanceId + "] 评估 (第" + evaluationCount + "次)");
        
        if (context == null) {
            return true;
        }
        
        String[] profiles = context.getEnvironment().getActiveProfiles();
        boolean result = profiles.length > 0 && "production".equals(profiles[0]);
        
        return result;
    }
}

/**
 * 条件实例化模拟器
 */
class ConditionInstantiationSimulator {
    
    /**
     * 模拟Spring的getCondition方法
     */
    public static Condition getCondition(String conditionClassName, ClassLoader classLoader) {
        try {
            System.out.println("🔧 模拟Spring的getCondition方法:");
            System.out.println("   1️⃣ 输入: 条件类名 = " + conditionClassName);
            
            // 加载类
            Class<?> conditionClass = Class.forName(conditionClassName);
            System.out.println("   2️⃣ 加载类: " + conditionClass.getName());
            
            // 🔥 关键：创建实例对象，不是返回Class
            Condition instance = (Condition) conditionClass.getDeclaredConstructor().newInstance();
            System.out.println("   3️⃣ 创建实例: " + instance.getClass().getSimpleName() + "@" + 
                             Integer.toHexString(instance.hashCode()));
            
            System.out.println("   4️⃣ 返回: Condition实例对象 (可直接调用matches方法)");
            
            return instance;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate condition: " + conditionClassName, e);
        }
    }
    
    /**
     * 演示完整的条件收集过程
     */
    public static List<Condition> collectConditions(String[] conditionClassNames) {
        System.out.println("\n📋 模拟完整的条件收集过程:");
        
        List<Condition> conditions = new ArrayList<>();
        
        for (String className : conditionClassNames) {
            System.out.println("\n处理条件类: " + className);
            Condition condition = getCondition(className, null);
            conditions.add(condition);
        }
        
        System.out.println("\n🎯 最终结果:");
        System.out.println("   conditions.size() = " + conditions.size());
        System.out.println("   存储类型: List<Condition> (实例对象集合)");
        System.out.println("   可直接使用: condition.matches() ✅");
        
        return conditions;
    }
}