package dev.travelpilot.tools;

import java.lang.reflect.Type;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

/** Tool methods already return readable text: pass it through instead of JSON-encoding it as a string. */
public class PlainTextResult implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        return result == null ? "" : result.toString();
    }
}
