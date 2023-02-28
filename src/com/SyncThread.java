package com;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncThread {
	private int rowCount = 0;
	private List<Long> threadIdList = new ArrayList<>();
	private List<Long> deadThreadIdList = new ArrayList<>();
	private Map<Long, String> deadThreadReason = new HashMap<>();
	private List<Map<String, Object>> resultList = new ArrayList<>();
	private List<Map<String, Object>> errorList = new ArrayList<>();
	private int procCount = 0;
	private int failCount = 0;
	private double procTime = 0;
	
	public synchronized int getRowCount(int addCount) {
		return this.rowCount += addCount;
	}

	public synchronized void addResultList(List<Map<String, Object>> list) {
		this.resultList.addAll(list);
	}

	public synchronized void addErrorList(List<Map<String, Object>> list) {
		this.errorList.addAll(list);
	}

	public synchronized List<Map<String, Object>> getResultList() {
		return this.resultList;
	}

	public synchronized List<Map<String, Object>> getErrorList() {
		return this.errorList;
	}

	public synchronized List<Long> getThreadIdList() {
		return this.threadIdList;
	}

	public synchronized void addThreadIdList(Long threadId) {
		this.threadIdList.add(threadId);
	}

	public synchronized void removeThreadIdList(Long threadId, String reason) {
		this.threadIdList.remove(threadId);
		this.deadThreadIdList.add(threadId);
		this.deadThreadReason.put(threadId, reason);
	}

	public synchronized List<Long> getDeadThreadIdList() {
		return this.deadThreadIdList;
	}

	public synchronized String getDeadThreadReason(Long threadId) {
		return this.deadThreadReason.get(threadId);
	}

	public synchronized void addProcCount(int count) {
		this.procCount += count;
	}

	public synchronized void addFailCount(int count) {
		this.failCount += count;
	}

	public synchronized int getProcCount() {
		return this.procCount;
	}

	public synchronized int getFailCount() {
		return this.failCount;
	}

	public double getProcTime() {
		return this.procTime;
	}

	public void addProcTime(double procTime) {
		this.procTime += procTime;
	}
}