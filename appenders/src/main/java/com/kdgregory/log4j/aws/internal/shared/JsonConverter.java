// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 *  Transforms a Map into a JSON string. Transforms strings, numbers, booleans,
 *  and null as expected; maps become nested objects; arrays and collections
 *  (list, set, whatever) become arrays; dates become strings formatted as
 *  ISO-8601 timestamps; anything else is converted to a string.
 */
public class JsonConverter
{
    public static String convert(Map<String,Object> map)
    {
        StringBuilder builder = new StringBuilder(1024);
        appendMap(builder, map);
        return builder.toString();
    }


    private static void append(StringBuilder builder, String key, Object value)
    {
        appendString(builder, key);
        builder.append(':');
        appendValue(builder, value);
    }


    private static void appendValue(StringBuilder builder, Object value)
    {
        if (value instanceof String)            appendString(builder, (String)value);
        else if (value instanceof Number)       appendNumber(builder, (Number)value);
        else if (value instanceof Boolean)      appendBoolean(builder, (Boolean)value);
        else if (value instanceof Date)         appendDate(builder, (Date)value);
        else if (value instanceof Object[])     appendArray(builder, (Object[])value);
        else if (value instanceof Collection)   appendCollection(builder, (Collection<Object>)value);
        else if (value instanceof Map)          appendMap(builder, (Map<String,Object>)value);
        else if (value == null)                 appendNull(builder);
        else                                    appendString(builder, String.valueOf(value));
    }


    private static void appendString(StringBuilder builder, String value)
    {
        builder.append("\"");
        int len = value.length();
        for (int ii = 0 ; ii < len ; ii++)
        {
            char c = value.charAt(ii);
            if (c == '"')
                builder.append("\\\"");
            else if (c == '\\')
                builder.append("\\\\");
            else if ((c >= 32) && (c <= 126))
                builder.append(c);
            else if ((c >= '\u00A0') && (c <= '\uD7FF'))
                builder.append(c);
            else if (c >= '\uE000')
                builder.append(c);
            else if (c == '\u0007')
                builder.append("\\b");
            else if (c == '\f')
                builder.append("\\f");
            else if (c == '\n')
                builder.append("\\n");
            else if (c == '\r')
                builder.append("\\r");
            else if (c == '\t')
                builder.append("\\t");
        }
        builder.append("\"");
    }


    private static void appendNumber(StringBuilder builder, Number value)
    {
        builder.append(value);
    }


    private static void appendBoolean(StringBuilder builder, Boolean value)
    {
        builder.append(value.booleanValue() ? "true" : "false");
    }


    private static void appendDate(StringBuilder builder, Date value)
    {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        appendString(builder, formatter.format(value));
    }


    private static void appendNull(StringBuilder builder)
    {
        builder.append("null");
    }


    private static void appendArray(StringBuilder builder, Object[] value)
    {
        builder.append("[");
        for (Object entry : value)
        {
            optAppendComma(builder, '[');
            appendValue(builder, entry);
        }
        builder.append("]");
    }


    private static void appendCollection(StringBuilder builder, Collection<Object> value)
    {
        builder.append("[");
        for (Object entry : value)
        {
            optAppendComma(builder, '[');
            appendValue(builder, entry);
        }
        builder.append("]");
    }

    private static void appendMap(StringBuilder builder, Map<String,Object> map)
    {
        builder.append("{");
        for (Map.Entry<String,Object> entry : map.entrySet())
        {
            optAppendComma(builder, '{');
            append(builder, entry.getKey(), entry.getValue());
        }
        builder.append("}");
    }


    private static void optAppendComma(StringBuilder builder, char valueInitiator)
    {
        if (builder.charAt(builder.length() - 1) != valueInitiator)
            builder.append(",");
    }
}
