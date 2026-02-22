package com.debug.aop_demo_tx_1;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;

@Service
public class UserService {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PostConstruct
	public void initTable() {
		jdbcTemplate.execute(
				"CREATE TABLE IF NOT EXISTS t_user_tx1 (" +
				"id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
				"name VARCHAR(100) NOT NULL" +
				") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
		);
	}

	/**
	 * 正常提交：插入一条数据，事务提交
	 */
	@Transactional
	public void save(String name) {
		jdbcTemplate.update("INSERT INTO t_user_tx1(name) VALUES(?)", name);
		System.out.println("[save] 插入: " + name);
	}

	/**
	 * 回滚演示：插入后抛异常，事务回滚
	 */
	@Transactional
	public void saveAndFail(String name) {
		jdbcTemplate.update("INSERT INTO t_user_tx1(name) VALUES(?)", name);
		System.out.println("[saveAndFail] 插入: " + name);
		throw new RuntimeException("模拟异常，触发回滚");
	}

	public int count() {
		Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_user_tx1", Integer.class);
		return c != null ? c : 0;
	}

	public void clear() {
		jdbcTemplate.update("DELETE FROM t_user_tx1");
	}
}
