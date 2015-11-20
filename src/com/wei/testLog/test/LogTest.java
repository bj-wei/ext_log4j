package com.wei.testLog.test;

import org.apache.log4j.Logger;

public class LogTest {

	private static Logger logger = Logger.getLogger(LogTest.class);
	
	@SuppressWarnings("static-access")
	public static void main(String[] args) throws Exception {
		for (int i = 0; i < 500; i++) {
			printLog(i);
		}
		Thread.currentThread().sleep(30000L);
		for (int i = 500; i < 1000; i++) {
			printLog(i);
		}
		Thread.currentThread().sleep(30000L);
		for (int i = 1000; i < 1500; i++) {
			printLog(i);
		}
		
	}

	public static void printLog(int i) {
//		System.out.println("This is println message." + i);

		// ��¼debug�������Ϣ
		logger.debug("This is debug message." + i);

		// ��¼info �������Ϣ 
		logger.info("This is info  message." + i);

		// ��¼error�������Ϣ
		logger.error("This is error message." + i);

		logger.trace("This is trace message." + i);

		logger.fatal("This is fatal message."+ i);

		logger.warn("This is warn  message." + i);
	}
}
