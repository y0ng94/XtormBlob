package com;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.windfire.apis.asysConnectDataPool;
import com.windfire.apis.asys.asysUsrElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ty.config.Config;
import ty.util.CommonUtil;
import ty.util.LogUtil;
import ty.util.TablePrinterUtil;

public class XtormApi {
	private static Logger log = LoggerFactory.getLogger(XtormApi.class);
	private String descr		= "BLOB";
	private String cClass		= "BASIC";
	private String eClass		= "IMAGE";
	private String uSClass		= "NONE";
	private String gateway		= "XTORM_MAIN";
	private String filterId		= "";
	private String filterParam	= "";

	public XtormApi(String descr, String cClass, String eClass, String uSClass, String gateway) {
		this.descr = descr;
		this.cClass = cClass;
		this.eClass = eClass;
		this.uSClass = uSClass;
		this.gateway = gateway;
	}

	public void setFilter(String filterId, String filterParam) {
		this.filterId = filterId==null?"":filterId;
		this.filterParam = filterParam==null?"":filterParam;
	}

	public Map<String, String> create(asysConnectDataPool con, InputStream inputStream, SyncThread syncThread) {
		Map<String, String> map = new HashMap<>();
		asysUsrElement usr = new asysUsrElement(con);

		usr.m_descr = descr;
		usr.m_cClassId = cClass;
		usr.m_eClassId = eClass;
		usr.m_userSClass = uSClass;

		int ret = -1;
		try {
			ret = usr.create(gateway, inputStream, filterId, filterParam);
		} catch (Exception e) {
			map.put("error", e.getMessage());
			LogUtil.error(log, "Unknown Error During Xtorm Create, {0} \n{1} \n{2}", e.getMessage(), e.getCause(), e.getStackTrace());
			
			monitoring(syncThread);
		}
		map.put("result", String.valueOf(ret));

		if (ret == 0) 
			map.put("elementId", usr.getShortID());
		else {
			map.put("error", usr.getLastError());
			LogUtil.error(log, "Error During Xtorm Create, {0}", usr.getLastError());
		}
		
		return map;
	}

	private void monitoring(SyncThread syncThread) {
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