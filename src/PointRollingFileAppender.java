

//package com.wei.proWithLog.appender;

import org.apache.log4j.Priority;
import org.apache.log4j.RollingFileAppender;

public class PointRollingFileAppender extends RollingFileAppender {

	public boolean isAsSevereAsThreshold(Priority priority) {
		return this.getThreshold().equals(priority);
	}
}