package org.bdp.collect.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.joda.time.DateTime;

import java.util.Date;

/**
 * Camel的自定义Processor，它是Camel灵活性的另一个体现，当数据处理中包含了一些相对复杂的自定义逻辑时，Camel允许开发人员使用编程的方式实现这些逻辑。
 * 对于本例中的时间处理，使用了Joda时间库
 */
public class DateShiftProcessor implements Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        Message message = exchange.getIn();
        Integer offset = message.getHeader("offset", Integer.class);
        Date firedTime = message.getHeader("firedTime", Date.class);
        DateTime dateTime = new DateTime(firedTime);
        DateTime shiftedTime = dateTime.minusSeconds(offset);
        message.setHeader("shiftedTime", shiftedTime.toDate());
    }
}
