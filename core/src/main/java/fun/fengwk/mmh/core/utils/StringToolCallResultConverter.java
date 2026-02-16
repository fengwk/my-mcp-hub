package fun.fengwk.mmh.core.utils;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * @author fengwk
 */
public class StringToolCallResultConverter implements ToolCallResultConverter {

    @Override
    public String convert(Object result, Type returnType) {
        if (result == null) {
            return "";
        }
        if (result instanceof CharSequence cs) {
            return cs.toString();
        }
        throw new IllegalStateException("unsupported result type: " + result.getClass());
    }

}
