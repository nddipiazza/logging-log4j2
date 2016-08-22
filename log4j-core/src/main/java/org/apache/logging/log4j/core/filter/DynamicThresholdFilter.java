/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.ContextData;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.ContextDataFactory;
import org.apache.logging.log4j.core.impl.ContextDataInjector;
import org.apache.logging.log4j.core.impl.ContextDataInjectorFactory;
import org.apache.logging.log4j.core.impl.MutableContextData;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.message.Message;

/**
 * Compare against a log level that is associated with a context value. By default the context is the
 * {@link ThreadContext}, but users may {@linkplain ContextDataInjectorFactory configure} a custom
 * {@link ContextDataInjector} which obtains context data from some other source.
 */
@Plugin(name = "DynamicThresholdFilter", category = Node.CATEGORY, elementType = Filter.ELEMENT_TYPE, printObject = true)
public final class DynamicThresholdFilter extends AbstractFilter {

    /**
     * Create the DynamicThresholdFilter.
     * @param key The name of the key to compare.
     * @param pairs An array of value and Level pairs.
     * @param defaultThreshold The default Level.
     * @param onMatch The action to perform if a match occurs.
     * @param onMismatch The action to perform if no match occurs.
     * @return The DynamicThresholdFilter.
     */
    @PluginFactory
    public static DynamicThresholdFilter createFilter(
            @PluginAttribute("key") final String key,
            @PluginElement("Pairs") final KeyValuePair[] pairs,
            @PluginAttribute("defaultThreshold") final Level defaultThreshold,
            @PluginAttribute("onMatch") final Result onMatch,
            @PluginAttribute("onMismatch") final Result onMismatch) {
        final Map<String, Level> map = new HashMap<>();
        for (final KeyValuePair pair : pairs) {
            map.put(pair.getKey(), Level.toLevel(pair.getValue()));
        }
        final Level level = defaultThreshold == null ? Level.ERROR : defaultThreshold;
        return new DynamicThresholdFilter(key, map, level, onMatch, onMismatch);
    }
    private Level defaultThreshold = Level.ERROR;
    private final String key;
    private final ContextDataInjector injector = ContextDataInjectorFactory.createInjector();
    private Map<String, Level> levelMap = new HashMap<>();

    private DynamicThresholdFilter(final String key, final Map<String, Level> pairs, final Level defaultLevel,
                                   final Result onMatch, final Result onMismatch) {
        super(onMatch, onMismatch);
        Objects.requireNonNull(key, "key cannot be null");
        this.key = key;
        this.levelMap = pairs;
        this.defaultThreshold = defaultLevel;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equalsImpl(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final DynamicThresholdFilter other = (DynamicThresholdFilter) obj;
        if (defaultThreshold == null) {
            if (other.defaultThreshold != null) {
                return false;
            }
        } else if (!defaultThreshold.equals(other.defaultThreshold)) {
            return false;
        }
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if (levelMap == null) {
            if (other.levelMap != null) {
                return false;
            }
        } else if (!levelMap.equals(other.levelMap)) {
            return false;
        }
        return true;
    }

    private Result filter(final Level level, final ContextData contextMap) {
        final String value = contextMap.getValue(key);
        if (value != null) {
            Level ctxLevel = levelMap.get(value);
            if (ctxLevel == null) {
                ctxLevel = defaultThreshold;
            }
            return level.isMoreSpecificThan(ctxLevel) ? onMatch : onMismatch;
        }
        return Result.NEUTRAL;

    }

    @Override
    public Result filter(final LogEvent event) {
        return filter(event.getLevel(), event.getContextData());
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final Message msg,
                         final Throwable t) {
        return filter(level, currentContextData());
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final Object msg,
                         final Throwable t) {
        return filter(level, currentContextData());
    }

    @Override
    public Result filter(final Logger logger, final Level level, final Marker marker, final String msg,
                         final Object... params) {
        return filter(level, currentContextData());
    }

    private ContextData currentContextData() {
        return injector.injectContextData(null, reusableInstance());
    }

    private MutableContextData reusableInstance() {
        // TODO if (Constants.ENABLE_THREADLOCALS) return thread-local instance
        return ContextDataFactory.createContextData(); // creates temporary object
    }

    public String getKey() {
        return this.key;
    }

    public Map<String, Level> getLevelMap() {
        return levelMap;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCodeImpl();
        result = prime * result + ((defaultThreshold == null) ? 0 : defaultThreshold.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((levelMap == null) ? 0 : levelMap.hashCode());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("key=").append(key);
        sb.append(", default=").append(defaultThreshold);
        if (levelMap.size() > 0) {
            sb.append('{');
            boolean first = true;
            for (final Map.Entry<String, Level> entry : levelMap.entrySet()) {
                if (!first) {
                    sb.append(", ");
                    first = false;
                }
                sb.append(entry.getKey()).append('=').append(entry.getValue());
            }
            sb.append('}');
        }
        return sb.toString();
    }
}
