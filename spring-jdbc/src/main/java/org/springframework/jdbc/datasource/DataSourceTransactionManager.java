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

package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.transaction.PlatformTransactionManager} implementation
 * for a single JDBC {@link javax.sql.DataSource}. This class is capable of working
 * in any environment with any JDBC driver, as long as the setup uses a
 * {@code javax.sql.DataSource} as its {@code Connection} factory mechanism.
 * Binds a JDBC {@code Connection} from the specified {@code DataSource} to the
 * current thread, potentially allowing for one thread-bound {@code Connection}
 * per {@code DataSource}.
 *
 * <p><b>Note: The {@code DataSource} that this transaction manager operates on
 * needs to return independent {@code Connection}s.</b> The {@code Connection}s
 * typically come from a connection pool but the {@code DataSource} must not return
 * specifically scoped or constrained {@code Connection}s. This transaction manager
 * will associate {@code Connection}s with thread-bound transactions, according
 * to the specified propagation behavior. It assumes that a separate, independent
 * {@code Connection} can be obtained even during an ongoing transaction.
 *
 * <p>Application code is required to retrieve the JDBC {@code Connection} via
 * {@link DataSourceUtils#getConnection(DataSource)} instead of a standard
 * EE-style {@link DataSource#getConnection()} call. Spring classes such as
 * {@link org.springframework.jdbc.core.JdbcTemplate} use this strategy implicitly.
 * If not used in combination with this transaction manager, the
 * {@link DataSourceUtils} lookup strategy behaves exactly like the native
 * {@code DataSource} lookup; it can thus be used in a portable fashion.
 *
 * <p>Alternatively, you can allow application code to work with the standard
 * EE-style lookup pattern {@link DataSource#getConnection()}, for example
 * for legacy code that is not aware of Spring at all. In that case, define a
 * {@link TransactionAwareDataSourceProxy} for your target {@code DataSource},
 * and pass that proxy {@code DataSource} to your DAOs which will automatically
 * participate in Spring-managed transactions when accessing it.
 *
 * <p>Supports custom isolation levels, and timeouts which get applied as
 * appropriate JDBC statement timeouts. To support the latter, application code
 * must either use {@link org.springframework.jdbc.core.JdbcTemplate}, call
 * {@link DataSourceUtils#applyTransactionTimeout} for each created JDBC
 * {@code Statement}, or go through a {@link TransactionAwareDataSourceProxy}
 * which will create timeout-aware JDBC {@code Connection}s and {@code Statement}s
 * automatically.
 *
 * <p>Consider defining a {@link LazyConnectionDataSourceProxy} for your target
 * {@code DataSource}, pointing both this transaction manager and your DAOs to it.
 * This will lead to optimized handling of "empty" transactions, i.e. of transactions
 * without any JDBC statements executed. A {@code LazyConnectionDataSourceProxy} will
 * not fetch an actual JDBC {@code Connection} from the target {@code DataSource}
 * until a {@code Statement} gets executed, lazily applying the specified transaction
 * settings to the target {@code Connection}.
 *
 * <p>This transaction manager supports nested transactions via the JDBC 3.0
 * {@link java.sql.Savepoint} mechanism. The
 * {@link #setNestedTransactionAllowed "nestedTransactionAllowed"} flag defaults
 * to "true", since nested transactions will work without restrictions on JDBC
 * drivers that support savepoints (such as the Oracle JDBC driver).
 *
 * <p>This transaction manager can be used as a replacement for the
 * {@link org.springframework.transaction.jta.JtaTransactionManager} in the single
 * resource case, as it does not require a container that supports JTA, typically
 * in combination with a locally defined JDBC {@code DataSource} (e.g. a Hikari
 * connection pool). Switching between this local strategy and a JTA environment
 * is just a matter of configuration!
 *
 * <p>As of 4.3.4, this transaction manager triggers flush callbacks on registered
 * transaction synchronizations (if synchronization is generally active), assuming
 * resources operating on the underlying JDBC {@code Connection}. This allows for
 * setup analogous to {@code JtaTransactionManager}, in particular with respect to
 * lazily registered ORM resources (e.g. a Hibernate {@code Session}).
 *
 * <p><b>NOTE: As of 5.3, {@link org.springframework.jdbc.support.JdbcTransactionManager}
 * is available as an extended subclass which includes commit/rollback exception
 * translation, aligned with {@link org.springframework.jdbc.core.JdbcTemplate}.</b>
 *
 * @author Juergen Hoeller
 * @since 02.05.2003
 * @see #setNestedTransactionAllowed
 * @see java.sql.Savepoint
 * @see DataSourceUtils#getConnection(javax.sql.DataSource)
 * @see DataSourceUtils#applyTransactionTimeout
 * @see DataSourceUtils#releaseConnection
 * @see TransactionAwareDataSourceProxy
 * @see LazyConnectionDataSourceProxy
 * @see org.springframework.jdbc.core.JdbcTemplate
 * @see org.springframework.jdbc.support.JdbcTransactionManager
 */
@SuppressWarnings("serial")
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
		implements ResourceTransactionManager, InitializingBean {

	@Nullable
	private DataSource dataSource;

	private boolean enforceReadOnly = false;


	/**
	 * Create a new {@code DataSourceTransactionManager} instance.
	 * A {@code DataSource} has to be set to be able to use it.
	 * @see #setDataSource
	 */
	public DataSourceTransactionManager() {
		setNestedTransactionAllowed(true);
	}

	/**
	 * Create a new {@code DataSourceTransactionManager} instance.
	 * @param dataSource the JDBC DataSource to manage transactions for
	 */
	public DataSourceTransactionManager(DataSource dataSource) {
		this();
		setDataSource(dataSource);
		afterPropertiesSet();
	}


	/**
	 * Set the JDBC {@code DataSource} that this instance should manage transactions for.
	 * <p>This will typically be a locally defined {@code DataSource}, for example a
	 * Hikari connection pool. Alternatively, you can also manage transactions for a
	 * non-XA {@code DataSource} fetched from JNDI. For an XA {@code DataSource},
	 * use {@link org.springframework.transaction.jta.JtaTransactionManager} instead.
	 * <p>The {@code DataSource} specified here should be the target {@code DataSource}
	 * to manage transactions for, not a {@link TransactionAwareDataSourceProxy}.
	 * Only data access code may work with {@code TransactionAwareDataSourceProxy} while
	 * the transaction manager needs to work on the underlying target {@code DataSource}.
	 * If there is nevertheless a {@code TransactionAwareDataSourceProxy} passed in,
	 * it will be unwrapped to extract its target {@code DataSource}.
	 * <p><b>The {@code DataSource} passed in here needs to return independent
	 * {@code Connection}s.</b> The {@code Connection}s typically come from a
	 * connection pool but the {@code DataSource} must not return specifically
	 * scoped or constrained {@code Connection}s, just possibly lazily fetched.
	 * @see LazyConnectionDataSourceProxy
	 */
	public void setDataSource(@Nullable DataSource dataSource) {
		if (dataSource instanceof TransactionAwareDataSourceProxy) {
			// If we got a TransactionAwareDataSourceProxy, we need to perform transactions
			// for its underlying target DataSource, else data access code won't see
			// properly exposed transactions (i.e. transactions for the target DataSource).
			this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
		}
		else {
			this.dataSource = dataSource;
		}
	}

	/**
	 * Return the JDBC {@code DataSource} that this instance manages transactions for.
	 */
	@Nullable
	public DataSource getDataSource() {
		return this.dataSource;
	}

	/**
	 * Obtain the {@code DataSource} for actual use.
	 * @return the DataSource (never {@code null})
	 * @throws IllegalStateException in case of no DataSource set
	 * @since 5.0
	 */
	protected DataSource obtainDataSource() {
		DataSource dataSource = getDataSource();
		Assert.state(dataSource != null, "No DataSource set");
		return dataSource;
	}

	/**
	 * Specify whether to enforce the read-only nature of a transaction
	 * (as indicated by {@link TransactionDefinition#isReadOnly()})
	 * through an explicit statement on the transactional connection:
	 * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
	 * <p>The exact treatment, including any SQL statement executed on the connection,
	 * can be customized through {@link #prepareTransactionalConnection}.
	 * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
	 * hint that Spring applies by default. In contrast to that standard JDBC hint,
	 * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
	 * where data manipulation statements are strictly disallowed. Also, on Oracle,
	 * this read-only mode provides read consistency for the entire transaction.
	 * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
	 * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
	 * this strong enforcement needs to be applied explicitly, e.g. through this flag.
	 * @since 4.3.7
	 * @see #prepareTransactionalConnection
	 */
	public void setEnforceReadOnly(boolean enforceReadOnly) {
		this.enforceReadOnly = enforceReadOnly;
	}

	/**
	 * Return whether to enforce the read-only nature of a transaction
	 * through an explicit statement on the transactional connection.
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	public boolean isEnforceReadOnly() {
		return this.enforceReadOnly;
	}

	@Override
	public void afterPropertiesSet() {
		if (getDataSource() == null) {
			throw new IllegalArgumentException("Property 'dataSource' is required");
		}
	}


	@Override
	public Object getResourceFactory() {
		return obtainDataSource();
	}

	@Override
	protected Object doGetTransaction() {
		/*
			forcus 创建 DataSourceTransactionObject 对象 (父类 JdbcTransactionObjectSupport 中有核心字段)
			不过刚创建时，一切都是默认值(null/false)
			{
				JdbcTransactionObjectSupport
					private ConnectionHolder connectionHolder;     // 数据库连接持有者
					private Integer previousIsolationLevel;        // 事务前的隔离级别（用于恢复）
					private boolean readOnly = false;              // 只读标记
					private boolean savepointAllowed = false;      // 是否允许保存点
				DataSourceTransactionObject
					private boolean newConnectionHolder;            // 是否是新创建的连接持有者
					private boolean mustRestoreAutoCommit;          // 事务结束后是否需要恢复 autoCommit
			}
		 */
		DataSourceTransactionObject txObject = new DataSourceTransactionObject();
		// 对于JDBC来说, savepointAllowed  = true,因为其支持savepoint机制(但是暂时不关心了,使用的比较少)
		txObject.setSavepointAllowed(isNestedTransactionAllowed());
		// forcus 获取资源，但是第一次进来的时候是为null的
		// forcus 如果是 REQUIRED事务方法 调用 REQUIRED事务方法(当然需要是一个线程～)，那么这里getResource()返回的就不是null了
		// 而是上一个事务方法早就放进去了的
		// forcus 如果是内层REQUIRED_NEW事务方法，那么在这里和内层REQUIRED事务方法一样,获取到的是外层事务对应的connectionHolder
		ConnectionHolder conHolder =
				(ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
		// 第一次是保存null到txObject中
		// 第二个参数为false：代表着 这个连接不是“我”新创建的，而是从resource中获取到的(可能就为null)
		// forcus 如果是第二个REQUIRED事务方法，这里的conHolder指向的是第一个REQUIRED事务方法所获取到的conn
		// forcus 此时的第二个参数false 代表的则是当前txObject内部所指向的conn，不是一个新连接
		// 但是需要注意的是，txObject则是新的哦～
		// forcus 对于REQUIRED_NEW来说，这里创建的txObject对象，目前还是指向的是外层事务方法对应的conn,并且第二个参数为false
		txObject.setConnectionHolder(conHolder, false);
		return txObject;
	}

	@Override
	protected boolean isExistingTransaction(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
	}

	/*
		transaction：DataSourceTransactionObject
		definition：DelegatingTransactionAttribute
	 */
	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		Connection con = null;

		try {
			/*
				forcus 获取数据库连接，有两种情况
					1.  !txObject.hasConnectionHolder() = connectionHolder == null
						这通常是首次创建事务时会命中的条件，同样也是最常见的场景
					2. !txObject.hasConnectionHolder() = true 但是 isSynchronizedWithTransaction() = true (这代表事务被同步标记)
						通常发生在 REQUEST_NEW 的传播行为中(外部事务持connection，并且事务是活跃的)，这个时候需要获取新连接

				forcus
					对于内层REQUIRED_NEW事务方法来说：
					1.  !txObject.hasConnectionHolder() = true (此时的txObject内的conn已经被清理保存起来了)
					2. 直接从连接池中再获取一个新的连接，并且保存到txObject中，后续就和之前讲解REQUIRED是一样的了
				而对于提交和回滚，全部当作新事务来操作，不再赘述
				但是需要注意的是，内层REQUIRED_NEW事务方法抛出异常后，异常会继续抛到外层事务方法中，关键点在于外层事务如何处理异常
				如果同样是抛出，那么最终内外层事务都会回滚，否则如果外层事务方法“吞掉”了异常，那么外层事务是不会回滚的
			 */
			if (!txObject.hasConnectionHolder() ||
					txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
				// forcus 获取新连接 - conncetion
				// 这里的 obtainDataSource() 会返回this.dataSource字段(配置的HikariDataSource等连接池)
				// getConnection() ：从连接池获取连接
				Connection newCon = obtainDataSource().getConnection();
				if (logger.isDebugEnabled()) {
					logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
				}
				/*
					1. 使用 ConnectionHolder 包装刚才获取到的 Cnnection
					2. 将 ch 保存到 DataSourceTransactionObject，并且设置 newConnectionHolder 属性为 true
				 */
				txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
			}
			/*
					 设置 connection 的 synchronizedWithTransaction 属性为 true
					 标记当前  ConnectionHolder 已与事务同步
						 回顾上面的条件判断：下次如果另一个事务（如 REQUIRES_NEW）再进入 doBegin()，
						 发现 isSynchronizedWithTransaction() = true，就知道这个连接已经被占用，必须获取新连接
						 这就是防止同一个 Connection 被两个事务同时使用的关键保护机制
			 */
			txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
			// 获取真实的connection对象（）
			con = txObject.getConnectionHolder().getConnection();

			/*
				这个方法只干两件事情：
					1. 如果当前配置了只读属性，那么设置到connection中
						- 这会告知 JDBC 驱动和数据库：这是只读事务，可以做优化（如 MySQL 的 InnoDB 会跳过为修改操作准备的锁等开销）
					2. 如果当前配置了非默认隔离级别，那么设置到connection中
			 */
			Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
			// 保存到txObject对象中
			txObject.setPreviousIsolationLevel(previousIsolationLevel);
			txObject.setReadOnly(definition.isReadOnly());

			// Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
			// so we don't want to do it unnecessarily (for example if we've explicitly
			// configured the connection pool to set it already).
			/*
				forcus 关闭自动提交(开启事务的本质)
				MySQL 默认 autoCommit = true，即每条 SQL 都是一个独立事务，执行后自动提交。
					当调用 con.setAutoCommit(false) 时：
					JDBC 驱动会向 MySQL 发送 SET autocommit=0（或在某些驱动中隐式 BEGIN）
					此后所有 SQL 都在同一个事务中，直到显式调用 commit() 或 rollback()
					Spring 的"开启事务"没有什么黑魔法，本质就是这一行 JDBC 调用
			 */
			if (con.getAutoCommit()) {
				txObject.setMustRestoreAutoCommit(true); // 记录"我改过 autoCommit"，事务结束后需要恢复
				if (logger.isDebugEnabled()) {
					logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
				}
				con.setAutoCommit(false); // forcus 关键代码 - 关闭connection的自动提交
			}
			// 什么都没做 skip
			prepareTransactionalConnection(con, definition);
			/*
				forcus 核心代码
				回忆之前的 isExistingTransaction() 的判断 (这个是用来判断 是否已经存在事务的判断)
					protected boolean isExistingTransaction(Object transaction) {
						DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
						return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
					}
				下次再有事务方法进来的时候，就会进入到 handleExistingTransaction() 处理传播行为
			 */
			txObject.getConnectionHolder().setTransactionActive(true);
			// 设置超时时间
			int timeout = determineTimeout(definition);
			if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
				txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
			}

			// Bind the connection holder to the thread.
			// 这里在上面才刚设置过 - true
			if (txObject.isNewConnectionHolder()) {
				// forcus 绑定到当前线程上
				TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
			}
		}
		// forcus 异常处理
		catch (Throwable ex) {
			if (txObject.isNewConnectionHolder()) { // 只有新建的连接才需要释放
				DataSourceUtils.releaseConnection(con, obtainDataSource());
				txObject.setConnectionHolder(null, false);
			}
			throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
		}
	}

	@Override
	protected Object doSuspend(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
		// forcus 删除指向外层事务对应conn的引用
		txObject.setConnectionHolder(null);
		// forcus 从 threadlocal 中移除 datasource -> connectionHolder
		return TransactionSynchronizationManager.unbindResource(obtainDataSource());
	}

	@Override
	protected void doResume(@Nullable Object transaction, Object suspendedResources) {
		TransactionSynchronizationManager.bindResource(obtainDataSource(), suspendedResources);
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Committing JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.commit(); // forcus 核心其实就是通过conn来进行数据提交
		}
		catch (SQLException ex) {
			throw translateException("JDBC commit", ex);
		}
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		Connection con = txObject.getConnectionHolder().getConnection();
		if (status.isDebug()) {
			logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
		}
		try {
			con.rollback();
		}
		catch (SQLException ex) {
			throw translateException("JDBC rollback", ex);
		}
	}

	@Override
	protected void doSetRollbackOnly(DefaultTransactionStatus status) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
		if (status.isDebug()) {
			logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
					"] rollback-only");
		}
		// forcus 打全局标记
		txObject.setRollbackOnly();
	}

	@Override
	protected void doCleanupAfterCompletion(Object transaction) {
		DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

		// Remove the connection holder from the thread, if exposed.
		// 在前面绑定了资源,在这里就解绑资源
		// forcus 并且只有最外层的事务方法才有资格解绑资源
		if (txObject.isNewConnectionHolder()) {
			TransactionSynchronizationManager.unbindResource(obtainDataSource());
		}

		// Reset connection.
		// forcus 重置connection,这里就回答了我之前的问题,connection在被归还到连接池之前,spring会将修改过的配置进行复原
		Connection con = txObject.getConnectionHolder().getConnection();
		try {
			// forcus 如果之前修改过自动提交(true -> false),那么在这里复原(false -> true)
			if (txObject.isMustRestoreAutoCommit()) {
				con.setAutoCommit(true);
			}
			// forcus 恢复 connecton 隔离级别 与 只读属性
			DataSourceUtils.resetConnectionAfterTransaction(
					con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
		}
		catch (Throwable ex) {
			logger.debug("Could not reset JDBC Connection after transaction", ex);
		}

		if (txObject.isNewConnectionHolder()) {
			if (logger.isDebugEnabled()) {
				logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
			}
			DataSourceUtils.releaseConnection(con, this.dataSource);
		}
		// 清空connectionHolder
		txObject.getConnectionHolder().clear();
	}


	/**
	 * Prepare the transactional {@code Connection} right after transaction begin.
	 * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
	 * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
	 * and the transaction definition indicates a read-only transaction.
	 * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
	 * and may work with other databases as well. If you'd like to adapt this treatment,
	 * override this method accordingly.
	 * @param con the transactional JDBC Connection
	 * @param definition the current transaction definition
	 * @throws SQLException if thrown by JDBC API
	 * @since 4.3.7
	 * @see #setEnforceReadOnly
	 */
	protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
			throws SQLException {

		if (isEnforceReadOnly() && definition.isReadOnly()) {
			try (Statement stmt = con.createStatement()) {
				stmt.executeUpdate("SET TRANSACTION READ ONLY");
			}
		}
	}

	/**
	 * Translate the given JDBC commit/rollback exception to a common Spring
	 * exception to propagate from the {@link #commit}/{@link #rollback} call.
	 * <p>The default implementation throws a {@link TransactionSystemException}.
	 * Subclasses may specifically identify concurrency failures etc.
	 * @param task the task description (commit or rollback)
	 * @param ex the SQLException thrown from commit/rollback
	 * @return the translated exception to throw, either a
	 * {@link org.springframework.dao.DataAccessException} or a
	 * {@link org.springframework.transaction.TransactionException}
	 * @since 5.3
	 */
	protected RuntimeException translateException(String task, SQLException ex) {
		return new TransactionSystemException(task + " failed", ex);
	}


	/**
	 * DataSource transaction object, representing a ConnectionHolder.
	 * Used as transaction object by DataSourceTransactionManager.
	 */
	private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

		private boolean newConnectionHolder;

		private boolean mustRestoreAutoCommit;

		public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
			super.setConnectionHolder(connectionHolder);
			this.newConnectionHolder = newConnectionHolder;
		}

		public boolean isNewConnectionHolder() {
			return this.newConnectionHolder;
		}

		public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
			this.mustRestoreAutoCommit = mustRestoreAutoCommit;
		}

		public boolean isMustRestoreAutoCommit() {
			return this.mustRestoreAutoCommit;
		}

		public void setRollbackOnly() {
			// forcus 打在connection上，对相同事务，不同事务方法，保证了事务的正常回滚
			getConnectionHolder().setRollbackOnly();
		}

		@Override
		public boolean isRollbackOnly() {
			return getConnectionHolder().isRollbackOnly();
		}

		@Override
		public void flush() {
			if (TransactionSynchronizationManager.isSynchronizationActive()) {
				TransactionSynchronizationUtils.triggerFlush();
			}
		}
	}

}
