package com.debug.demo_scheduled_1;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 定时任务业务类
 *
 * 演示三种调度方式：fixedRate、fixedDelay、cron
 */
@Service
public class MyScheduledTask {

	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	/**
	 * fixedRate：每隔 2 秒执行一次（从上一次【开始】算起）
	 * 不管上一次是否执行完，到时间就触发
	 */
	@Scheduled(fixedRate = 2000)
	@Transactional
	public void fixedRateTask() {
		System.out.println("[fixedRate]  " + LocalDateTime.now().format(FMT)
				+ " 线程: " + Thread.currentThread().getName());
	}

	/**
	 * fixedDelay：上一次执行【完成】后，等待 3 秒再执行下一次
	 */
	@Scheduled(fixedDelay = 3000)
	@Transactional
	public void fixedDelayTask() {
		System.out.println("[fixedDelay] " + LocalDateTime.now().format(FMT)
				+ " 线程: " + Thread.currentThread().getName());
	}

	/**
	 * cron 表达式：每隔 5 秒执行一次
	 * 秒 分 时 日 月 星期
	 */
	@Scheduled(cron = "0/5 * * * * ?")
	@Transactional
	public void cronTask() {
		System.out.println("[cron]       " + LocalDateTime.now().format(FMT)
				+ " 线程: " + Thread.currentThread().getName());
	}
}
