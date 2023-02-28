package ty.module;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * @author	: y0ng94
 * @version	: 1.0
 */
public class ConnectionPool {
	private String dbUrl;			// Database url
	private String dbUser;			// Database user account
	private String dbPassword;		// Database user password

	private static final String checkQuery = "select 1";	// Health check query
	private static final int defaultPoolSize = 10;			// Default database connection pool size
	private int maxSize = defaultPoolSize;					// Maximum database connection pool size
	private int conSize = 0;								// Current database connection pool size

	Stack<Connection> standByPool = new Stack<>();			// Standby state database connection pool
	Set<Connection> activePool = new HashSet<>();			// Active state database connection pool

	// Constructor
	public ConnectionPool(String driverClassName, String dbUrl, String dbUser, String dbPassword, int maxSize) throws ClassNotFoundException {
		Class.forName(driverClassName);

		this.dbUrl = dbUrl;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;
		this.maxSize = maxSize;
	}

	/**
	 * Return the connection from standby pool. If standby pool is empty, generates an exception.
	 * @return Connection
	 * @throws SQLException
	 */
	public synchronized Connection getConnection() throws SQLException {
		Connection con = null;

		if (isFull()) { throw new SQLException("Out of the connection pool size"); }

		con = popConnection();

		if (con == null) { con = newConnection(); }

		con = setAlive(con);

		return con;
	}

	/**
	 * Return the connection to standby pool.
	 * @param Connection
	 * @throws SQLException
	 */
	public synchronized void returnConnection(Connection con) throws SQLException {
		if (con == null) { throw new NullPointerException(); }
		if (!activePool.remove(con)) { throw new SQLException("The connection is returned already or it isn't for this pool"); }
		standByPool.push(con);
	}

	/**
	 * Return the connection from standby pool. Add a connection to the active pool.
	 * @return Connection
	 */
	private Connection popConnection() {
		Connection con = null;

		if (standByPool.size() > 0) {
			con = standByPool.pop();
			activePool.add(con);
		}

		return con;
	}

	/**
	 * Create a new connection. Increase the number of connections and add them to the active pool.
	 * @return Connection
	 * @throws SQLException
	 */
	private Connection newConnection() throws SQLException {
		Connection con = createConnection();
		conSize++;
		activePool.add(con);
		return con;
	}

	/**
	 * Create a new connection
	 * @return Connection
	 * @throws SQLException
	 */
	private Connection createConnection() throws SQLException {
		Connection con = null;
		con = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
		return con;
	}

	/**
	 * If the connection is null, create and return a new connection.
	 * @param Connection
	 * @return Connection
	 * @throws SQLException
	 */
	private Connection setAlive(Connection con) throws SQLException {
		if (isAlive(con)) { return con; }

		// If connection isn't alive, reconnect
		activePool.remove(con);
		conSize--;
		con.close();
		
		con = newConnection();
		activePool.add(con);
		conSize++;

		return con;
	}

	/**
	 * By running a sql to check if the connection is alive
	 * @param Connection
	 * @return boolean
	 */
	private boolean isAlive(Connection con) {
		try (Statement statement = con.createStatement()) {
			statement.executeQuery(checkQuery);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	/**
	 * Check if connection is full
	 * @return boolean
	 */
	private synchronized boolean isFull() {
		return ((standByPool.size() == 0) && (conSize >= maxSize));
	}

	/**
	 * Close all connections.
	 * @throws SQLException
	 */
	public void closeConnection() throws SQLException {
		for (Connection con : standByPool)
			con.close();

		for (Connection con : activePool)
			con.close();
	}
}
