package com;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import com.windfire.apis.asysConnectDataPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ty.config.Config;
import ty.module.ConnectionPool;
import ty.util.CommonUtil;
import ty.util.LogUtil;
import ty.util.TablePrinterUtil;

public class BlobCallable implements Callable<Integer> {
	private static Logger log = LoggerFactory.getLogger(BlobCallable.class);
	private SyncThread syncThread = null;
	private ConnectionPool cPool = null;
	private asysConnectDataPool xPool = null;
	private XtormApi xApi = null;
	private String query = "";
	private int rowSize = 0;

	public BlobCallable(SyncThread syncThread, ConnectionPool cPool, asysConnectDataPool xPool) {
		this.syncThread = syncThread;
		this.cPool = cPool;
		this.xPool = xPool;
		this.query = Config.getConfig("DB.BLOB.QUERY");
		this.xApi = new XtormApi(Config.getConfig("ENGINE.DESCR"), Config.getConfig("ENGINE.CCLASS"), Config.getConfig("ENGINE.ECLASS"), Config.getConfig("ENGINE.USCLASS"), Config.getConfig("ENGINE.GATEWAY"));
		this.xApi.setFilter(Config.getConfig("ENGINE.FILTER.ID"), Config.getConfig("ENGINE.FILTER.PARAM"));
		this.rowSize = Config.getIntConfig("COUNT.GET");
	}

	@Override
	public Integer call() throws Exception {
		LogUtil.info(log, "Start Thread [ #{0} ]", Thread.currentThread().getId());
		
		long runTime = System.currentTimeMillis();						// Thread run time
		long partTime = 0;												// Run time of one part in the thread
		int sleepTime = Config.getIntConfig("THREAD.SLEEP");		// Thread interval time
		int totalProcCount = 0;											// Total count in thread for return
		int totalFailCount = 0;											// Fail count in thread
		List<Map<String, String>> colListMap = new LinkedList<>();		// Column Data

		syncThread.addThreadIdList(Thread.currentThread().getId());

		while (true) {
			List<Map<String, Object>> resultListMap = new ArrayList<>();	// Result Data
			List<Map<String, Object>> errorListMap = new ArrayList<>();		// Error Data
			int lastRowNum = syncThread.getRowCount(rowSize);
			String bindQuery = query.replaceFirst("\\?", String.valueOf(lastRowNum));
			int procCount = 0;
			int failCount = 0;

			partTime = System.currentTimeMillis();
			bindQuery = bindQuery.replace("?", String.valueOf(lastRowNum - rowSize));
			
			LogUtil.debug(log, "Running Thread [ #{0} ] Execute Select Query  : \" {1} \"", Thread.currentThread().getId(), bindQuery);

			// Connection from connection stack
			Connection con = null;

			// Get Column Data
			if (colListMap.isEmpty()) {
				con = getConnection();

				try (PreparedStatement statement = con.prepareStatement(query)) {
					statement.setInt(1, lastRowNum);
					statement.setInt(2, lastRowNum - rowSize);

					try (ResultSet resultSet = statement.executeQuery()) {
						ResultSetMetaData metaData = resultSet.getMetaData();

						for (int i=1; i<=metaData.getColumnCount(); i++) {
							Map<String, String> map = new HashMap<>();
							map.put("column", metaData.getColumnLabel(i));
							map.put("type", String.valueOf(metaData.getColumnType(i)));
							map.put("typename", metaData.getColumnTypeName(i));
							colListMap.add(map);
						}
					}
				} catch (SQLException e) {
					LogUtil.error(log, "SQL Error, {0} \n{1} \n{2}", e.getMessage(), e.getCause(), e.getStackTrace());
					syncThread.removeThreadIdList(Thread.currentThread().getId(), e.getMessage());;
					monitoring();
					throw e;
				} catch (Exception e) {
					LogUtil.error(log, "Error, {0} \n{1} \n{2}", e.getMessage(), e.getCause(), e.getStackTrace());
					syncThread.removeThreadIdList(Thread.currentThread().getId(), e.getMessage());;
					monitoring();
					throw e;
				} finally {
					cPool.returnConnection(con);
				}
			}

			con = getConnection();

			try (PreparedStatement statement = con.prepareStatement(query)) {
				statement.setInt(1, lastRowNum);
				statement.setInt(2, lastRowNum - rowSize);
	
				try (ResultSet resultSet = statement.executeQuery()) {
					int rowNum = 0;
					
					while (resultSet.next()) {
						rowNum++;

						Map<String, Object> row = new HashMap<>();
	
						for (Map<String, String> col : colListMap) {
							String colName = col.get("column");
							int colType = Integer.parseInt(col.get("type"));
	
							if (colType == java.sql.Types.CHAR) {
								String data = resultSet.getString(colName);
								if (data != null) { data = data.trim(); }
								row.put(colName, data);
							} else if (colType == java.sql.Types.INTEGER || colType == java.sql.Types.NUMERIC) {
								row.put(colName, resultSet.getInt(colName));
							} else if (colType == java.sql.Types.VARCHAR) {
								row.put(colName, resultSet.getString(colName));
							} else if (colType == java.sql.Types.BLOB || colType == java.sql.Types.BINARY) {
								row.get("filename");

								InputStream stream = resultSet.getBinaryStream(colName);
								Map<String, String> xResult = new HashMap<>();

								try {
									procCount++;
									if (stream != null) {
										try {
											xResult = xApi.create(xPool, stream, syncThread);
										} catch (Exception e) {
											throw e;
										}

										if (xResult.get("result").equals("0"))
											row.put("elementId", xResult.get("elementId"));
										else {
											row.put("error", xResult.get("error"));
											failCount++;
										}
									} else
										throw new Exception("Stream is Empty or Null");
								} catch (Exception ie) {
									row.put("error", ie.getMessage());
									failCount++;
									LogUtil.error(log, "Error during xtorm create, {0}", ie.getMessage());
								} finally {
									if (stream != null) { stream.close(); }
								}
							} else {
								row.put(colName, resultSet.getObject(colName));
							}
						}
	
						if (row.get("elementId") != null)
							resultListMap.add(row);
						else
							errorListMap.add(row);
					}

					if (rowNum == 0)
						break;
				} finally {
					totalProcCount += procCount;
					totalFailCount += failCount;
					syncThread.addProcCount(procCount);
					syncThread.addFailCount(failCount);
				}
			} catch (SQLException e) {
				LogUtil.error(log, "SQL Error, {0} \n{1} \n{2}", e.getMessage(), e.getCause(), e.getStackTrace());
				syncThread.removeThreadIdList(Thread.currentThread().getId(), e.getMessage());;
				monitoring();
				throw e;
			} finally {
				cPool.returnConnection(con);

				double elapsedTime = CommonUtil.getTimeElapsed(partTime);	// Run time of a one time

				// Recoding
				syncThread.addResultList(resultListMap);
				syncThread.addErrorList(errorListMap);
				syncThread.addProcTime(elapsedTime);
	
				LogUtil.debug(log, "Running Thread [ #{0} ] [ Add Count : +{1} ] [ One Cycle Result : ( {2} / {3} ) ]",
								Thread.currentThread().getId(), procCount, (procCount-failCount), failCount);
				LogUtil.debug(log, "Running Thread [ #{0} ] [ Accrue Count : {1} ( {2} / {3} ) ]",
								Thread.currentThread().getId(), totalProcCount, (totalProcCount-totalFailCount), totalFailCount);
				LogUtil.info(log, "Running Thread [ #{0} ] [ Run Time : {1}s ] [ Performance Speed : {2} c/s ]",
								Thread.currentThread().getId(), elapsedTime, CommonUtil.getDivision(procCount, (int)elapsedTime));
				LogUtil.debug(log, "Sleep Thread [ #{0} ] [ Sleep Time : {1}s ]", Thread.currentThread().getId(), sleepTime);
				
				monitoring();
			}
			
			// Thread interval
			Thread.sleep(sleepTime);
		}

		double totalElapsedTime = CommonUtil.getTimeElapsed(runTime);

		LogUtil.info(log, "End Thread [ #{0} ] [ Total Result : {1} ( {2} / {3} ) ]", Thread.currentThread().getId(), totalProcCount, (totalProcCount-totalFailCount), totalFailCount);
		LogUtil.info(log, "End Thread [ #{0} ] [ Run Time : {1}s ] [ Performance Speed : {2} c/s ]",
						Thread.currentThread().getId(), totalElapsedTime, CommonUtil.getDivision(totalProcCount, (int)totalElapsedTime));

		syncThread.removeThreadIdList(Thread.currentThread().getId(), "Complete");

		monitoring();

		return totalProcCount;
	}

	/**
	 * Get db connection from connection stack
	 * @return asysConnectDataPool
	 * @throws InterruptedException
	 */
	private Connection getConnection() throws InterruptedException {
		try {
			return cPool.getConnection();
		} catch (SQLException e) {
			Thread.sleep(1000);
			return getConnection();
		}
	}

	private void monitoring() {
		int threadCount = Config.getIntConfig("THREAD.COUNT");
		int procCount = syncThread.getProcCount();
		int failCount = syncThread.getFailCount();
		List<String> colList = new ArrayList<>();
		Map<String, Object> valMap = new HashMap<>();
		List<Long> threadIdList = new ArrayList<>(syncThread.getThreadIdList());
		List<Long> deadThreadIdList = syncThread.getDeadThreadIdList();

		threadIdList.removeAll(deadThreadIdList);
		threadIdList.addAll(deadThreadIdList);

		for (Long threadId : threadIdList)  {
			colList.add(threadId.toString());
			String reason = syncThread.getDeadThreadReason(threadId);
			valMap.put(threadId.toString(), reason==null?"Alive":reason);
		}

		colList.sort(Comparator.naturalOrder());
		String threadTable = TablePrinterUtil.printTable(colList.toArray(new String[colList.size()]), valMap, 60);

		LogUtil.info(log, "Monitoring Thread [ All ] [ Total Result : {0} ( {1} / {2} ) ] [ Performance Speed : {3} c/s ]\n{4}", procCount, (procCount - failCount), failCount, CommonUtil.getDivision(procCount, syncThread.getProcTime()/threadCount), threadTable);
	}
}
