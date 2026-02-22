package com.debug.aop_demo_transactional.dao;

import com.debug.aop_demo_transactional.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 用户数据访问层（MySQL真实操作）
 */
@Repository
public class UserDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 初始化：创建表
     */
    @PostConstruct
    public void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS t_user (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "age INT" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        jdbcTemplate.execute(sql);
        System.out.println("[DAO] 表 t_user 初始化完成");
    }

    /**
     * 保存用户
     */
    public void save(User user) {
        String sql = "INSERT INTO t_user (name, age) VALUES (?, ?)";
        jdbcTemplate.update(sql, user.getName(), user.getAge());
        System.out.println("[DAO] 保存用户: " + user);
    }

    /**
     * 根据ID查询用户
     */
    public User findById(Long id) {
        String sql = "SELECT * FROM t_user WHERE id = ?";
        List<User> users = jdbcTemplate.query(sql, new UserRowMapper(), id);
        return users.isEmpty() ? null : users.get(0);
    }

    /**
     * 查询所有用户
     */
    public List<User> findAll() {
        String sql = "SELECT * FROM t_user";
        return jdbcTemplate.query(sql, new UserRowMapper());
    }

    /**
     * 清空数据（用于测试）
     */
    public void clear() {
        String sql = "DELETE FROM t_user";
        jdbcTemplate.update(sql);
        System.out.println("[DAO] 数据已清空");
    }

    /**
     * 打印当前所有数据
     */
    public void printAll() {
        List<User> users = findAll();
        System.out.println("[DAO] 当前数据库中的用户:");
        if (users.isEmpty()) {
            System.out.println("  (空)");
        } else {
            users.forEach(user -> System.out.println("  " + user));
        }
    }

    /**
     * 用户RowMapper
     */
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setName(rs.getString("name"));
            user.setAge(rs.getInt("age"));
            return user;
        }
    }
}
