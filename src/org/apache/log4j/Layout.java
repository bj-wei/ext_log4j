/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j;

import org.apache.log4j.spi.OptionHandler;
import org.apache.log4j.spi.LoggingEvent;

/**
   Extend this abstract class to create your own log layout format.
   <p>实现OptionHandler接口，该接口定义了一个activateOptions()方法，
   	用于配置文件解析完后，同时应用所有配置，以解决有些配置存在依赖的情况。
   
   @author Ceki G&uuml;lc&uuml;

*/
  
public abstract class Layout implements OptionHandler {

  // Note that the line.separator property can be looked up even by
  // applets.
  public final static String LINE_SEP = System.getProperty("line.separator");
  public final static int LINE_SEP_LEN  = LINE_SEP.length();


  /**
     Implement this method to create your own layout format.
     <p>将LoggingEvent类中的信息格式化成一行日志。
  */
  abstract
  public
  String format(LoggingEvent event);

  /**
     Returns the content type output by this layout. The base class
     returns "text/plain". 
     <p>定义日志文件的内容类型，目前在Log4J中只是在SMTPAppender中用到，用于设置发送邮件的邮件内容类型。而Layout本身也只有HTMLLayout实现了它。
  */
  public
  String getContentType() {
    return "text/plain";
  }

  /**
     Returns the header for the layout format. The base class returns
     <code>null</code>.  
     <p>定义日志文件的头，目前在Log4J中只是在HTMLLayout中实现了它。
     */
  public
  String getHeader() {
    return null;
  }

  /**
     Returns the footer for the layout format. The base class returns
     <code>null</code>.  
     <p>定义日志文件的尾，目前在Log4J中只是HTMLLayout中实现了它。
     */
  public
  String getFooter() {
    return null;
  }



  /**
     If the layout handles the throwable object contained within
     {@link LoggingEvent}, then the layout should return
     <code>false</code>. Otherwise, if the layout ignores throwable
     object, then the layout should return <code>true</code>.

     <p>The {@link SimpleLayout}, {@link TTCCLayout}, {@link
     PatternLayout} all return <code>true</code>. The {@link
     org.apache.log4j.xml.XMLLayout} returns <code>false</code>.
     
     <p>定义当前layout是否处理异常类型。在Log4J中，不支持处理异常类型的有：TTCLayout、PatternLayout、SimpleLayout。

     @since 0.8.4 */
  abstract
  public
  boolean ignoresThrowable();

}
