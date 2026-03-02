package com.debug.boot.verify;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringBootExceptionReporter;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * 【第五章验证器】异常处理与 FailureAnalyzer 机制验证
 * ═══════════════════════════════════════════════════════════════════════════════
 * <p>
 * 通过运行时打印日志，验证《SpringBoot启动全流程深度分析》第五章中的核心结论。
 * <p>
 * 验证要点：
 * 1. SpringBootExceptionReporter SPI 注册（文档 5.4.1 节）
 * 2. FailureAnalyzer SPI 加载列表和数量（文档 5.6 节）
 * 3. FailureAnalysisReporter SPI 加载（文档 5.4.5 节）
 * 4. AbstractFailureAnalyzer 泛型异常类型路由验证（文档 5.4.4 节）
 * 5. SpringBootExceptionHandler 线程检查（文档 5.4.6 节）
 */
@Component
@Order(Integer.MIN_VALUE + 1) // 在 StartupFlowVerifier 之后执行
public class FailureAnalyzerVerifier implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║     【第五章】异常处理与 FailureAnalyzer 机制 — 文档结论运行验证报告              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");

        verifyExceptionReporterSPI();
        verifyFailureAnalyzerList();
        verifyFailureAnalysisReporter();
        verifyAbstractFailureAnalyzerGenericRouting();
        verifySpringBootExceptionHandler();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  ✅ 第五章所有验证项打印完毕，请对比文档中的描述！");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println();
    }

    /**
     * 验证点 1：SpringBootExceptionReporter SPI 注册
     * 文档结论（5.4.1 节）：spring.factories 中 SpringBootExceptionReporter 只有一个实现 FailureAnalyzers
     */
    private void verifyExceptionReporterSPI() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ 验证点 1：SpringBootExceptionReporter SPI 注册（文档 5.4.1 节）                 │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");

        List<String> reporterNames = SpringFactoriesLoader.loadFactoryNames(
                SpringBootExceptionReporter.class, getClass().getClassLoader());

        System.out.println("  [运行数据] SpringBootExceptionReporter 注册数量: " + reporterNames.size());
        for (int i = 0; i < reporterNames.size(); i++) {
            System.out.println("    [" + (i + 1) + "] " + reporterNames.get(i));
        }

        boolean hasFailureAnalyzers = reporterNames.stream()
                .anyMatch(name -> name.contains("FailureAnalyzers"));
        System.out.println("  [文档结论] SpringBootExceptionReporter 的唯一实现是 FailureAnalyzers");
        System.out.println("  [验证结果] " + (hasFailureAnalyzers && reporterNames.size() == 1
                ? "✅ 一致 — 只有 FailureAnalyzers 一个实现"
                : "⚠️ 数量为 " + reporterNames.size() + "，请检查是否有自定义扩展"));
    }

    /**
     * 验证点 2：FailureAnalyzer SPI 加载列表和数量
     * 文档结论（5.6 节）：spring-boot.jar 中注册了 20 个 FailureAnalyzer
     */
    private void verifyFailureAnalyzerList() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ 验证点 2：FailureAnalyzer SPI 加载列表（文档 5.6 节）                           │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");

        List<String> analyzerNames = SpringFactoriesLoader.loadFactoryNames(
                FailureAnalyzer.class, getClass().getClassLoader());

        System.out.println("  [运行数据] 从所有 spring.factories 加载到的 FailureAnalyzer 总数: " + analyzerNames.size());
        System.out.println();

        int bootCount = 0;
        int autoconfigureCount = 0;
        int otherCount = 0;

        for (int i = 0; i < analyzerNames.size(); i++) {
            String name = analyzerNames.get(i);
            String source;
            if (name.contains("autoconfigure")) {
                source = " ← spring-boot-autoconfigure.jar";
                autoconfigureCount++;
            } else if (name.contains("boot")) {
                source = " ← spring-boot.jar";
                bootCount++;
            } else {
                source = " ← 其他模块";
                otherCount++;
            }
            System.out.println("    [" + String.format("%2d", i + 1) + "] " + getSimpleName(name) + source);
        }

        System.out.println();
        System.out.println("  [运行数据] 按模块统计:");
        System.out.println("    - spring-boot.jar 贡献: " + bootCount + " 个");
        System.out.println("    - spring-boot-autoconfigure.jar 贡献: " + autoconfigureCount + " 个");
        if (otherCount > 0) {
            System.out.println("    - 其他模块贡献: " + otherCount + " 个");
        }
        System.out.println("  [文档结论] spring-boot.jar 中注册了 20 个 FailureAnalyzer");
        System.out.println("  [验证结果] " + (bootCount == 20
                ? "✅ spring-boot.jar 中确实是 20 个"
                : "⚠️ spring-boot.jar 中实际为 " + bootCount + " 个，请检查版本差异"));

        // 验证几个关键的 FailureAnalyzer 是否存在
        System.out.println();
        System.out.println("  [关键 Analyzer 验证]");
        String[] keyAnalyzers = {
                "PortInUseFailureAnalyzer",
                "NoUniqueBeanDefinitionFailureAnalyzer",
                "BeanCurrentlyInCreationFailureAnalyzer",
                "BindFailureAnalyzer",
                "ConnectorStartFailureAnalyzer",
                "NoSuchBeanDefinitionFailureAnalyzer"
        };
        for (String key : keyAnalyzers) {
            boolean found = analyzerNames.stream().anyMatch(name -> name.contains(key));
            System.out.println("    " + (found ? "✅" : "❌") + " " + key + (found ? " — 已注册" : " — 未找到！"));
        }
    }

    /**
     * 验证点 3：FailureAnalysisReporter SPI 加载
     * 文档结论（5.4.5 节）：唯一实现是 LoggingFailureAnalysisReporter
     */
    private void verifyFailureAnalysisReporter() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ 验证点 3：FailureAnalysisReporter SPI 加载（文档 5.4.5 节）                     │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");

        // FailureAnalysisReporter 接口在 diagnostics 包中
        List<String> reporterNames = SpringFactoriesLoader.loadFactoryNames(
                org.springframework.boot.diagnostics.FailureAnalysisReporter.class,
                getClass().getClassLoader());

        System.out.println("  [运行数据] FailureAnalysisReporter 注册数量: " + reporterNames.size());
        for (int i = 0; i < reporterNames.size(); i++) {
            System.out.println("    [" + (i + 1) + "] " + reporterNames.get(i));
        }

        boolean hasLogging = reporterNames.stream()
                .anyMatch(name -> name.contains("LoggingFailureAnalysisReporter"));
        System.out.println("  [文档结论] FailureAnalysisReporter 的唯一实现是 LoggingFailureAnalysisReporter");
        System.out.println("  [验证结果] " + (hasLogging && reporterNames.size() == 1
                ? "✅ 一致 — 只有 LoggingFailureAnalysisReporter 一个实现"
                : "⚠️ 数量为 " + reporterNames.size()));
    }

    /**
     * 验证点 4：AbstractFailureAnalyzer 泛型异常类型路由
     * 文档结论（5.4.4 节）：通过 ResolvableType 解析泛型参数，实现异常类型路由
     */
    private void verifyAbstractFailureAnalyzerGenericRouting() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ 验证点 4：AbstractFailureAnalyzer 泛型异常类型路由（文档 5.4.4 节）               │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");

        // 通过反射验证 PortInUseFailureAnalyzer 的泛型参数解析
        try {
            Class<?> abstractClass = Class.forName(
                    "org.springframework.boot.diagnostics.AbstractFailureAnalyzer");
            Class<?> portAnalyzerClass = Class.forName(
                    "org.springframework.boot.diagnostics.analyzer.PortInUseFailureAnalyzer");

            // 使用 ResolvableType 解析泛型参数（和源码一样的方式）
            org.springframework.core.ResolvableType resolvableType =
                    org.springframework.core.ResolvableType.forClass(abstractClass, portAnalyzerClass);
            Class<?> causeType = resolvableType.resolveGeneric();

            System.out.println("  [运行数据] PortInUseFailureAnalyzer 的泛型参数解析:");
            System.out.println("    - AbstractFailureAnalyzer<T> 中的 T 解析为: " + (causeType != null ? causeType.getName() : "null"));
            System.out.println("  [文档结论] PortInUseFailureAnalyzer 只处理 PortInUseException");
            System.out.println("  [验证结果] " + (causeType != null && causeType.getSimpleName().equals("PortInUseException")
                    ? "✅ 一致 — 泛型解析正确，只路由 PortInUseException"
                    : "❌ 不一致！解析结果为 " + causeType));

            // 验证 findCause 的异常链查找逻辑
            System.out.println();
            System.out.println("  [模拟验证] findCause() 异常链查找:");
            Exception innerEx = new org.springframework.boot.web.server.PortInUseException(8080);
            Exception wrapperEx = new RuntimeException("Wrapper", new IllegalStateException("Mid", innerEx));

            // 模拟 findCause 逻辑
            Throwable current = wrapperEx;
            int depth = 0;
            boolean found = false;
            while (current != null) {
                System.out.println("    [depth=" + depth + "] " + current.getClass().getSimpleName()
                        + ": " + current.getMessage()
                        + (causeType != null && causeType.isInstance(current) ? " ← ⭐ MATCH!" : ""));
                if (causeType != null && causeType.isInstance(current)) {
                    found = true;
                }
                current = current.getCause();
                depth++;
            }
            System.out.println("  [验证结果] " + (found
                    ? "✅ findCause() 能从 3 层异常链中找到 PortInUseException（depth=2）"
                    : "❌ 未找到匹配的异常"));

        } catch (ClassNotFoundException e) {
            System.out.println("  [错误] 类加载失败: " + e.getMessage());
        }
    }

    /**
     * 验证点 5：SpringBootExceptionHandler 线程检查
     * 文档结论（5.4.6 节）：只在 main 线程或 restartedMain 线程时才获取 ExceptionHandler
     */
    private void verifySpringBootExceptionHandler() {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ 验证点 5：SpringBootExceptionHandler 线程检查（文档 5.4.6 节）                   │");
        System.out.println("└──────────────────────────────────────────────────────────────────────────────┘");

        Thread currentThread = Thread.currentThread();
        String threadName = currentThread.getName();
        String threadGroupName = currentThread.getThreadGroup().getName();

        System.out.println("  [运行数据] 当前线程名: " + threadName);
        System.out.println("  [运行数据] 当前线程组: " + threadGroupName);

        boolean isMainThread = ("main".equals(threadName) || "restartedMain".equals(threadName))
                && "main".equals(threadGroupName);
        System.out.println("  [运行数据] 是否为 main 线程? " + isMainThread);
        System.out.println("  [文档结论] getSpringBootExceptionHandler() 只在 main/restartedMain 线程中返回非 null");
        System.out.println("  [说明] registerLoggedException() 的作用: 防止异常被 UncaughtExceptionHandler 重复打印");

        // 验证 UncaughtExceptionHandler
        Thread.UncaughtExceptionHandler handler = currentThread.getUncaughtExceptionHandler();
        System.out.println("  [运行数据] 当前线程的 UncaughtExceptionHandler: "
                + (handler != null ? handler.getClass().getName() : "null"));
    }

    // ═══════════════════════ 工具方法 ═══════════════════════

    private String getSimpleName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
