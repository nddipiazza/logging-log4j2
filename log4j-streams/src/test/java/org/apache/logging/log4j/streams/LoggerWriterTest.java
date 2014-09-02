package org.apache.logging.log4j.streams;

import java.io.StringWriter;
import java.io.Writer;

public class LoggerWriterTest extends AbstractLoggerWriterTest {

    protected StringWriter createWriter() {
        return null;
    }

    protected Writer createWriterWrapper() {
        return new LoggerWriter(getLogger(), LEVEL);
    }

}
