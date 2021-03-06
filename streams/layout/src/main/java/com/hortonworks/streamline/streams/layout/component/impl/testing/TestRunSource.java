/**
 * Copyright 2017 Hortonworks.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *   http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package com.hortonworks.streamline.streams.layout.component.impl.testing;

import com.hortonworks.streamline.streams.layout.component.Stream;
import com.hortonworks.streamline.streams.layout.component.StreamlineSource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source for topology test run. This class contains test records which will be emitted from test run Spout.
 */
public class TestRunSource extends StreamlineSource {
    private final Map<String, List<Map<String, Object>>> testRecordsForEachStream;

    public TestRunSource() {
        this(Collections.EMPTY_SET, Collections.EMPTY_MAP);
    }

    /**
     * Constructor.
     *
     * @param outputStreams output streams.
     * @param testRecordsForEachStream (output stream name) -> list of test record (each map represents a record)
     */
    public TestRunSource(Set<Stream> outputStreams, Map<String, List<Map<String, Object>>> testRecordsForEachStream) {
        super(outputStreams);
        this.testRecordsForEachStream = testRecordsForEachStream;
    }

    public Map<String, List<Map<String, Object>>> getTestRecordsForEachStream() {
        return testRecordsForEachStream;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestRunSource)) return false;
        if (!super.equals(o)) return false;

        TestRunSource that = (TestRunSource) o;

        return getTestRecordsForEachStream() != null ?
                getTestRecordsForEachStream().equals(that.getTestRecordsForEachStream()) :
                that.getTestRecordsForEachStream() == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getTestRecordsForEachStream() != null ? getTestRecordsForEachStream().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TestRunSource{" +
                "testRecordsForEachStream=" + testRecordsForEachStream +
                '}' + super.toString();
    }
}
