package com;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.windfire.apis.asysConnectDataPool;

import ty.config.Config;
import ty.module.ConnectionPool;
import ty.module.ExecutorHelper;
import ty.util.CommonUtil;
import ty.util.LogUtil;

public class Startup {
	private static Logger log = LoggerFactory.getLogger(Startup.class);
	private static String name = "Xtorm Blob";
	private Long runTime;
	private int resultCount = 0;

    public static void main(String[] args) {
		Startup run = new Startup();

		LogUtil.info(log, "Start {0}...", name);

		run.runTime = System.currentTimeMillis();

		// Argument Check
		if (args == null || args.length == 0) {
			LogUtil.error(log, "Required Argument is not Received, You have to Enter The Default Path as a Parameter");
		} else {
			// Initialize Configuration
			if (run.initConfig(args[0])) {
				LogUtil.info(log, "Initialize Pool Object");

				String engineIp = Config.getConfig("ENGINE.IP");
				int enginePort = Config.getIntConfig("ENGINE.PORT");
				String dbUrl = Config.getConfig("DB.URL");

				LogUtil.debug(log, "Target Xtorm Info : {0}:{1}", engineIp, String.valueOf(enginePort));
				LogUtil.debug(log, "Target DateBase Url : {0}", dbUrl);

				// Construct Pool
				asysConnectDataPool xPool = new asysConnectDataPool(engineIp, enginePort, Config.getConfig("ENGINE.APP"), Config.getConfig("ENGINE.ID"), Config.getConfig("ENGINE.PW"), Config.getIntConfig("ENGINE.TIMETOLIVE"));
				ConnectionPool cPool = null;
				try {
					cPool = new ConnectionPool(Config.getConfig("DB.DRIVER"), dbUrl, Config.getConfig("DB.ID"), Config.getConfig("DB.PW"), Config.getIntConfig("DB.POOLSIZE"));
				} catch (NumberFormatException | ClassNotFoundException | NullPointerException e) {
					LogUtil.error(log, e.getMessage());
					return;
				}

				LogUtil.info(log, "Initialize Executor");

				int threadCount = Config.getIntConfig("THREAD.COUNT");

				LogUtil.debug(log, "Thread Count : {0}", threadCount);

				// Initialize ExecutorService
				ExecutorHelper helper = new ExecutorHelper();
				helper.init(threadCount);
				
				// Declaration Callables
				List<Callable<Integer>> callables = new ArrayList<>();
				SyncThread syncThread = new SyncThread();
				for (int i=0; i<threadCount; i++)
					callables.add(new BlobCallable(syncThread, cPool, xPool));
				
				LogUtil.info(log, "Execute Blob Thread");

				try {
					// Execute Thread
					List<Integer> resultList = helper.execute(callables);
					
					for (Integer result : resultList)
						run.resultCount += result;
				} catch (InterruptedException | ExecutionException e) {
					LogUtil.error(log, e.getMessage());
				} catch (Exception e) {
					LogUtil.error(log, e.getMessage());
					e.printStackTrace();
				} finally {
					try {
						helper.shutdown(Config.getIntConfig("THREAD.SHUTDOWNTIMEOUT"));
					} catch (InterruptedException e) {
						LogUtil.error(log, e.getMessage());
					}
				}

				LogUtil.info(log, "Recoding Result");

				Long partTime = System.currentTimeMillis();
				String numberColName = Config.getConfig("DB.NUMBER.COL");
				String[] outputColNames = Config.getArrConfig("DB.OUTPUT.COL");
				String filePath = Config.getConfig("FILE.PATH");
				if (!(filePath.endsWith("/") || filePath.endsWith("\\")))
					filePath = filePath + File.pathSeparator;
					int writeCount = Config.getIntConfig("FILE.WRITECOUNT");

				String fileName = Config.getConfig("FILE.NAME");
				String fileEncoding = Config.getConfig("FILE.ENCODING");
				int currentLine = 0;
				Path resultFile = Paths.get(filePath + fileName);
				int resultFileNumber = 0;

				if (Files.exists(resultFile))
					resultFile = Paths.get(CommonUtil.makeBackFileName(filePath + fileName,  "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())));

				Path orgResultFile = resultFile;
				
				syncThread.getResultList().sort(Comparator.comparing(t -> (int)t.get("NUM")));

				for (Map<String, Object> result : syncThread.getResultList()) {
					currentLine++; 

					if (currentLine > writeCount) {
						resultFile = Paths.get(CommonUtil.makeBackFileName(orgResultFile.toAbsolutePath().toString(),  "_" + resultFileNumber));
						resultFileNumber++;
						currentLine = 1;
					}

					StringBuffer line = new StringBuffer();

					line.append(numberColName + " / " + result.get(numberColName) + "\t");
					line.append("elementId / " + result.get("elementId") + "\t");
					if (outputColNames.length == 0) {
						for (Map.Entry<String, Object> entry : result.entrySet()) {
							if (!entry.getKey().equals(numberColName) && !entry.getKey().equals("elementId"))
								line.append(entry.getKey() + " / " + entry.getValue() + "\t");
						}
					} else {
						for (String outputColName : outputColNames)
							line.append(outputColName + " / " + result.get(outputColName) + "\t");
					}
					line.append("\n");
					
					try {
						if (Files.exists(resultFile))
							Files.write(resultFile, line.toString().getBytes(fileEncoding), StandardOpenOption.APPEND);
						else {
							try {
								Thread.sleep(Config.getIntConfig("THREAD.SLEEP"));
							} catch (NumberFormatException | InterruptedException e) {
								e.printStackTrace();
							}
							Files.write(resultFile, line.toString().getBytes(fileEncoding), StandardOpenOption.CREATE_NEW);
						}
					} catch (IOException e) {
						LogUtil.error(log, "Error During Result File Writing, " + e.getMessage());
						return;
					}
				}

				LogUtil.info(log, "Complete Recoding Result [ {0}s ]", CommonUtil.getTimeElapsed(partTime));

				LogUtil.info(log, "Recoding Error Result");

				partTime = System.currentTimeMillis();
				String errorFileName = Config.getConfig("FILE.ERROR.NAME");
				currentLine = 0;
				Path errorFile = Paths.get(filePath + errorFileName);
				int errorFileNumber = 0;

				if (Files.exists(errorFile))
					errorFile = Paths.get(CommonUtil.makeBackFileName(filePath + errorFileName,  "_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())));
					
				Path orgErrorFile = errorFile;
				
				syncThread.getErrorList().sort(Comparator.comparing(t -> (int)t.get("NUM")));
				
				for (Map<String, Object> error : syncThread.getErrorList()) {
					currentLine++; 

					if (currentLine > writeCount) {
						errorFile = Paths.get(CommonUtil.makeBackFileName(orgErrorFile.toAbsolutePath().toString(),  "_" + errorFileNumber));
						errorFileNumber++;
						currentLine = 1;
					}

					StringBuffer line = new StringBuffer();

					line.append(numberColName + " / " + error.get(numberColName) + "\t");
					line.append("error / " + error.get("error") + "\t");
					if (outputColNames.length == 0) {
						for (Map.Entry<String, Object> entry : error.entrySet()) {
							if (!entry.getKey().equals(numberColName) && !entry.getKey().equals("error"))
								line.append(entry.getKey() + " / " + entry.getValue() + "\t");
						}
					} else {
						for (String outputColName : outputColNames)
							line.append(outputColName + " / " + error.get(outputColName) + "\t");
					}
					line.append("\n");
					
					try {
						if (Files.exists(errorFile))
							Files.write(errorFile, line.toString().getBytes(fileEncoding), StandardOpenOption.APPEND);
						else {
							try {
								Thread.sleep(Config.getIntConfig("THREAD.SLEEP"));
							} catch (NumberFormatException | InterruptedException e) {
								e.printStackTrace();
							}
							Files.write(errorFile, line.toString().getBytes(fileEncoding), StandardOpenOption.CREATE_NEW);
						}
					} catch (IOException e) {
						LogUtil.error(log, "Error During Error File Writing, " + e.getMessage());
						return;
					}
				}

				LogUtil.info(log, "Complete Recoding Error Result [ {0}s ]", CommonUtil.getTimeElapsed(partTime));

				LogUtil.info(log, "Complete Blob Thread [ {0} ]", run.resultCount);

				if (xPool != null)
					xPool.close();
			}
		}

		double totalTimeElapsed = CommonUtil.getTimeElapsed(run.runTime);
		LogUtil.info(log, "End {0}... [ Run Time : {1}s ] [ Performance Speed : {2} c/s ]", name, totalTimeElapsed, CommonUtil.getDivision(run.resultCount, (int)totalTimeElapsed));
		return;
    }

	// Initialize Configuration
	private boolean initConfig(String baseDir) {                                   
		LogUtil.info(log, "Setting Configuration... [ {0}/conf/conf.properties ]", baseDir);

		long partTime = System.currentTimeMillis();

		try {
			Config.setConfig(baseDir + "/conf/conf.properties");
			Config.setConfig("baseDir", baseDir);
			LogUtil.info(log, "Configuration Detail...\n{0}", Config.getConfigTable());
			return true;
		} catch (IOException e) {
			LogUtil.error(log, "Error during Configuration Setting, " + e.getMessage());
			return false;
		} finally {
			LogUtil.debug(log, "Complete Setting Configuration... [ Run Time : {0}s ]", CommonUtil.getTimeElapsed(partTime));
		}
	}
}