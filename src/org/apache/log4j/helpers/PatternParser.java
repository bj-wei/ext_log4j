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
package org.apache.log4j.helpers;

import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.helpers.AbsoluteTimeDateFormat;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.LocationInfo;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

// Contributors:   Nelson Minar <(nelson@monkey.org>
//                 Igor E. Poteryaev <jah@mail.ru>
//                 Reinhard Deschler <reinhard.deschler@web.de>

/**
   Most of the work of the {@link org.apache.log4j.PatternLayout} class
   is delegated to the PatternParser class.

   <p>It is this class that parses conversion patterns and creates
   a chained list of {@link OptionConverter OptionConverters}.
   
   <p>负责解析 <code>PatternLayout</code> 中设置的 <code>conversion pattern</code>.
   <p>它将 conversion pattern 解析出一个链状的 PatternConverter ，而后在每次格式化 LoggingEvent 实例是，只需要遍历该链即可

   @author <a href=mailto:"cakalijp@Maritz.com">James P. Cakalic</a>
   @author Ceki G&uuml;lc&uuml;
   @author Anders Kristensen

   @since 0.8.2
*/
public class PatternParser {

  private static final char ESCAPE_CHAR = '%';

  private static final int LITERAL_STATE = 0;
  private static final int CONVERTER_STATE = 1;
  private static final int DOT_STATE = 3;
  private static final int MIN_STATE = 4;
  private static final int MAX_STATE = 5;

  static final int FULL_LOCATION_CONVERTER = 1000;
  static final int METHOD_LOCATION_CONVERTER = 1001;
  static final int CLASS_LOCATION_CONVERTER = 1002;
  static final int LINE_LOCATION_CONVERTER = 1003;
  static final int FILE_LOCATION_CONVERTER = 1004;

  static final int RELATIVE_TIME_CONVERTER = 2000;
  static final int THREAD_CONVERTER = 2001;
  static final int LEVEL_CONVERTER = 2002;
  static final int NDC_CONVERTER = 2003;
  static final int MESSAGE_CONVERTER = 2004;

  int state;
  protected StringBuffer currentLiteral = new StringBuffer(32);
  protected int patternLength;
  protected int i;
  PatternConverter head;
  PatternConverter tail;
  protected FormattingInfo formattingInfo = new FormattingInfo();
  protected String pattern;

  public
  PatternParser(String pattern) {
    this.pattern = pattern;
    patternLength =  pattern.length();
    state = LITERAL_STATE;
  }

  private
  void  addToList(PatternConverter pc) {
    if(head == null) {
      head = tail = pc;
    } else {
      tail.next = pc;
      tail = pc;
    }
  }

  protected
  String extractOption() {
    if((i < patternLength) && (pattern.charAt(i) == '{')) {
      int end = pattern.indexOf('}', i);
      if (end > i) {
	String r = pattern.substring(i + 1, end);
	i = end+1;
	return r;
      }
    }
    return null;
  }


  /**
     The option is expected to be in decimal and positive. In case of
     error, zero is returned.  */
  protected
  int extractPrecisionOption() {
    String opt = extractOption();
    int r = 0;
    if(opt != null) {
      try {
	r = Integer.parseInt(opt);
	if(r <= 0) {
	    LogLog.error(
	        "Precision option (" + opt + ") isn't a positive integer.");
	    r = 0;
	}
      }
      catch (NumberFormatException e) {
	LogLog.error("Category option \""+opt+"\" not a decimal integer.", e);
      }
    }
    return r;
  }

  /**
   * 在解析conversion pattern时，PatternParser使用了有限状态机的方法：
   * 即PatternParser定义了五种状态，初始化时LITERAL_STATE，当遍历完成，则退出；
   * 否则，如果当前字符不是’%’，则将该字符添加到currentLiteral中，继续遍历；
   * 否则，若下一字符是’%’，则将其当做基本字符处理，若下一字符是’n’，则添加换行符，
   * 否则，将之前收集的literal字符创建LiteralPatternConverter实例，
   * 添加到相应的PatternConverter链中，清空currentLiteral实例，并添加下一字符，
   * 解析器进入CONVERTER_STATE状态
   * */
  public
  PatternConverter parse() {
    char c;
    i = 0;
    while(i < patternLength) {
      c = pattern.charAt(i++);
      switch(state) {
      case LITERAL_STATE:
        // In literal state, the last char is always a literal.
        if(i == patternLength) {
          currentLiteral.append(c);
          continue;
        }
        if(c == ESCAPE_CHAR) {
        	//当前字符是’%’
          // peek at the next char.
          switch(pattern.charAt(i)) {
          case ESCAPE_CHAR:
            currentLiteral.append(c);
            i++; // move pointer
            break;
          case 'n':
            currentLiteral.append(Layout.LINE_SEP);	//currentLiteral添加换行符
            i++; // move pointer
            break;
          default:
            if(currentLiteral.length() != 0) {
              addToList(new LiteralPatternConverter(
                                                  currentLiteral.toString()));
              //LogLog.debug("Parsed LITERAL converter: \""
              //           +currentLiteral+"\".");
            }
            currentLiteral.setLength(0);
            currentLiteral.append(c); // append %
            state = CONVERTER_STATE;
            formattingInfo.reset();
          }
        }
        else {
          currentLiteral.append(c);
        }
        break;
      case CONVERTER_STATE:
    	  //对CONVERTER_STATE状态，若当前字符是’-‘，则表明左对齐；若遇到’.’，则进入DOT_STATE状态；
    	  //若遇到数字，则进入MIN_STATE状态；
    	  //若遇到其他字符，则根据字符解析出不同的PatternConverter，
    	  //并且如果存在可选项信息（’{}’中的信息），一起提取出来，并将状态重新设置成LITERAL_STATE状态
	currentLiteral.append(c);
	switch(c) {
	case '-':
	  formattingInfo.leftAlign = true;
	  break;
	case '.':
	  state = DOT_STATE;
	  break;
	default:
	  if(c >= '0' && c <= '9') {
	    formattingInfo.min = c - '0';
	    state = MIN_STATE;
	  }
	  else
	    finalizeConverter(c);
	} // switch
	break;
      case MIN_STATE:
    	  //进入MIN_STATE状态，首先判断当期字符是否为数字，若是，则继续计算精度的最小值；
    	  //若遇到’.’，则进入DOT_STATE状态；否则，根据字符解析出不同的PatternConverter，
    	  //并且如果存在可选项信息（’{}’中的信息），一起提取出来，并将状态重新设置成LITERAL_STATE状态
	currentLiteral.append(c);
	if(c >= '0' && c <= '9')
	  formattingInfo.min = formattingInfo.min*10 + (c - '0');
	else if(c == '.')
	  state = DOT_STATE;
	else {
	  finalizeConverter(c);
	}
	break;
      case DOT_STATE:
    	  //进入DOT_STATE状态，如果当前字符是数字，则进入MAX_STATE状态；格式出错，回到LITERAL_STATE状态
	currentLiteral.append(c);
	if(c >= '0' && c <= '9') {
	  formattingInfo.max = c - '0';
	   state = MAX_STATE;
	}
	else {
	  LogLog.error("Error occured in position "+i
		     +".\n Was expecting digit, instead got char \""+c+"\".");
	  state = LITERAL_STATE;
	}
	break;
      case MAX_STATE:
    	  //进入MAX_STATE状态，若为数字，则继续计算最大精度值，
    	  //否则，根据字符解析出不同的PatternConverter，
    	  //并且如果存在可选项信息（’{}’中的信息），一起提取出来，并将状态重新设置成LITERAL_STATE状态
	currentLiteral.append(c);
	if(c >= '0' && c <= '9')
	  formattingInfo.max = formattingInfo.max*10 + (c - '0');
	else {
	  finalizeConverter(c);
	  state = LITERAL_STATE;
	}
	break;
      } // switch
    } // while
    if(currentLiteral.length() != 0) {
      addToList(new LiteralPatternConverter(currentLiteral.toString()));
      //LogLog.debug("Parsed LITERAL converter: \""+currentLiteral+"\".");
    }
    return head;
  }

  protected
  void finalizeConverter(char c) {
    PatternConverter pc = null;
    switch(c) {
    case 'c':
      pc = new CategoryPatternConverter(formattingInfo,
					extractPrecisionOption());
      //LogLog.debug("CATEGORY converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'C':
      pc = new ClassNamePatternConverter(formattingInfo,
					 extractPrecisionOption());
      //LogLog.debug("CLASS_NAME converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'd':
      String dateFormatStr = AbsoluteTimeDateFormat.ISO8601_DATE_FORMAT;
      DateFormat df;
      String dOpt = extractOption();
      if(dOpt != null)
	dateFormatStr = dOpt;

      if(dateFormatStr.equalsIgnoreCase(
                                    AbsoluteTimeDateFormat.ISO8601_DATE_FORMAT))
	df = new  ISO8601DateFormat();
      else if(dateFormatStr.equalsIgnoreCase(
                                   AbsoluteTimeDateFormat.ABS_TIME_DATE_FORMAT))
	df = new AbsoluteTimeDateFormat();
      else if(dateFormatStr.equalsIgnoreCase(
                              AbsoluteTimeDateFormat.DATE_AND_TIME_DATE_FORMAT))
	df = new DateTimeDateFormat();
      else {
	try {
	  df = new SimpleDateFormat(dateFormatStr);
	}
	catch (IllegalArgumentException e) {
	  LogLog.error("Could not instantiate SimpleDateFormat with " +
		       dateFormatStr, e);
	  df = (DateFormat) OptionConverter.instantiateByClassName(
			           "org.apache.log4j.helpers.ISO8601DateFormat",
				   DateFormat.class, null);
	}
      }
      pc = new DatePatternConverter(formattingInfo, df);
      //LogLog.debug("DATE converter {"+dateFormatStr+"}.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'F':
      pc = new LocationPatternConverter(formattingInfo,
					FILE_LOCATION_CONVERTER);
      //LogLog.debug("File name converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'l':
      pc = new LocationPatternConverter(formattingInfo,
					FULL_LOCATION_CONVERTER);
      //LogLog.debug("Location converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'L':
      pc = new LocationPatternConverter(formattingInfo,
					LINE_LOCATION_CONVERTER);
      //LogLog.debug("LINE NUMBER converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'm':
      pc = new BasicPatternConverter(formattingInfo, MESSAGE_CONVERTER);
      //LogLog.debug("MESSAGE converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'M':
      pc = new LocationPatternConverter(formattingInfo,
					METHOD_LOCATION_CONVERTER);
      //LogLog.debug("METHOD converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'p':
      pc = new BasicPatternConverter(formattingInfo, LEVEL_CONVERTER);
      //LogLog.debug("LEVEL converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 'r':
      pc = new BasicPatternConverter(formattingInfo,
					 RELATIVE_TIME_CONVERTER);
      //LogLog.debug("RELATIVE time converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
    case 't':
      pc = new BasicPatternConverter(formattingInfo, THREAD_CONVERTER);
      //LogLog.debug("THREAD converter.");
      //formattingInfo.dump();
      currentLiteral.setLength(0);
      break;
      /*case 'u':
      if(i < patternLength) {
	char cNext = pattern.charAt(i);
	if(cNext >= '0' && cNext <= '9') {
	  pc = new UserFieldPatternConverter(formattingInfo, cNext - '0');
	  LogLog.debug("USER converter ["+cNext+"].");
	  formattingInfo.dump();
	  currentLiteral.setLength(0);
	  i++;
	}
	else
	  LogLog.error("Unexpected char" +cNext+" at position "+i);
      }
      break;*/
    case 'x':
      pc = new BasicPatternConverter(formattingInfo, NDC_CONVERTER);
      //LogLog.debug("NDC converter.");
      currentLiteral.setLength(0);
      break;
    case 'X':
      String xOpt = extractOption();
      pc = new MDCPatternConverter(formattingInfo, xOpt);
      currentLiteral.setLength(0);
      break;
    default:
      LogLog.error("Unexpected char [" +c+"] at position "+i
		   +" in conversion patterrn.");
      pc = new LiteralPatternConverter(currentLiteral.toString());
      currentLiteral.setLength(0);
    }

    addConverter(pc);
  }

  protected
  void addConverter(PatternConverter pc) {
    currentLiteral.setLength(0);
    // Add the pattern converter to the list.
    addToList(pc);
    // Next pattern is assumed to be a literal.
    state = LITERAL_STATE;
    // Reset formatting info
    formattingInfo.reset();
  }

  // ---------------------------------------------------------------------
  //                      PatternConverters
  // ---------------------------------------------------------------------

  private static class BasicPatternConverter extends PatternConverter {
    int type;

    BasicPatternConverter(FormattingInfo formattingInfo, int type) {
      super(formattingInfo);
      this.type = type;
    }

    public
    String convert(LoggingEvent event) {
      switch(type) {
      case RELATIVE_TIME_CONVERTER:
	return (Long.toString(event.timeStamp - LoggingEvent.getStartTime()));
      case THREAD_CONVERTER:
	return event.getThreadName();
      case LEVEL_CONVERTER:
	return event.getLevel().toString();
      case NDC_CONVERTER:
	return event.getNDC();
      case MESSAGE_CONVERTER: {
	return event.getRenderedMessage();
      }
      default: return null;
      }
    }
  }

  private static class LiteralPatternConverter extends PatternConverter {
    private String literal;

    LiteralPatternConverter(String value) {
      literal = value;
    }

    public
    final
    void format(StringBuffer sbuf, LoggingEvent event) {
      sbuf.append(literal);
    }

    public
    String convert(LoggingEvent event) {
      return literal;
    }
  }

  private static class DatePatternConverter extends PatternConverter {
    private DateFormat df;
    private Date date;

    DatePatternConverter(FormattingInfo formattingInfo, DateFormat df) {
      super(formattingInfo);
      date = new Date();
      this.df = df;
    }

    public
    String convert(LoggingEvent event) {
      date.setTime(event.timeStamp);
      String converted = null;
      try {
        converted = df.format(date);
      }
      catch (Exception ex) {
        LogLog.error("Error occured while converting date.", ex);
      }
      return converted;
    }
  }

  private static class MDCPatternConverter extends PatternConverter {
    private String key;

    MDCPatternConverter(FormattingInfo formattingInfo, String key) {
      super(formattingInfo);
      this.key = key;
    }

    public
    String convert(LoggingEvent event) {
      Object val = event.getMDC(key);
      if(val == null) {
	return null;
      } else {
	return val.toString();
      }
    }
  }


  private class LocationPatternConverter extends PatternConverter {
    int type;

    LocationPatternConverter(FormattingInfo formattingInfo, int type) {
      super(formattingInfo);
      this.type = type;
    }

    public
    String convert(LoggingEvent event) {
      LocationInfo locationInfo = event.getLocationInformation();
      switch(type) {
      case FULL_LOCATION_CONVERTER:
	return locationInfo.fullInfo;
      case METHOD_LOCATION_CONVERTER:
	return locationInfo.getMethodName();
      case LINE_LOCATION_CONVERTER:
	return locationInfo.getLineNumber();
      case FILE_LOCATION_CONVERTER:
	return locationInfo.getFileName();
      default: return null;
      }
    }
  }

  private static abstract class NamedPatternConverter extends PatternConverter {
    int precision;

    NamedPatternConverter(FormattingInfo formattingInfo, int precision) {
      super(formattingInfo);
      this.precision =  precision;
    }

    abstract
    String getFullyQualifiedName(LoggingEvent event);

    public
    String convert(LoggingEvent event) {
      String n = getFullyQualifiedName(event);
      if(precision <= 0)
	return n;
      else {
	int len = n.length();

	// We substract 1 from 'len' when assigning to 'end' to avoid out of
	// bounds exception in return r.substring(end+1, len). This can happen if
	// precision is 1 and the category name ends with a dot.
	int end = len -1 ;
	for(int i = precision; i > 0; i--) {
	  end = n.lastIndexOf('.', end-1);
	  if(end == -1)
	    return n;
	}
	return n.substring(end+1, len);
      }
    }
  }

  private class ClassNamePatternConverter extends NamedPatternConverter {

    ClassNamePatternConverter(FormattingInfo formattingInfo, int precision) {
      super(formattingInfo, precision);
    }

    String getFullyQualifiedName(LoggingEvent event) {
      return event.getLocationInformation().getClassName();
    }
  }

  private class CategoryPatternConverter extends NamedPatternConverter {

    CategoryPatternConverter(FormattingInfo formattingInfo, int precision) {
      super(formattingInfo, precision);
    }

    String getFullyQualifiedName(LoggingEvent event) {
      return event.getLoggerName();
    }
  }
}

