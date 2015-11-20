package org.apache.log4j;

import org.apache.log4j.Priority;
import org.apache.log4j.RollingFileAppender;

public class UntiRollingFileAppenderAppender extends RollingFileAppender  {

	public boolean isAsSevereAsThreshold(Priority priority) {
		return !this.getThreshold().equals(priority);
	}
}