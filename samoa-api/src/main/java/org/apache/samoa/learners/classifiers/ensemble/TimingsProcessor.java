package org.apache.samoa.learners.classifiers.ensemble;
/*
 * #%L
 * SAMOA
 * %%
 * Copyright (C) 2014 - 2015 Apache Software Foundation
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.topology.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TimingsProcessor implements Processor {
  private static final long serialVersionUID = -8831675956999798443L;
  private static final Logger logger = LoggerFactory.getLogger(TimingsProcessor.class);

  private Stream timingStream;
  private int numLocalStats;

  // Timings is a table whose each row is a collection of measurements, and each column one measurement from each LSP
  private Table<Integer, Integer, Long> timings;

  private DescriptiveStatistics meanStats = new DescriptiveStatistics();

  public TimingsProcessor(int numLocalStats) {
    this.numLocalStats = numLocalStats;
  }

  public TimingsProcessor(TimingsProcessor other) {
    this.numLocalStats = other.numLocalStats;
    this.timings = other.timings;
    this.meanStats = other.meanStats;
  }

  @Override
  public boolean process(ContentEvent event) {
    // Every time an event arrives, we add it to the correct row in the table, according the the measurement id
    // and local stats id it has. Once we collect all measurements for a particular measurement id (== num. local stats)
    // we calculate its overall statistics and print them out.
    // TODO: If by the end of the stream we haven't collected stats from all, we should prolly just print the remainder
    TimingsEvent tevent = (TimingsEvent) event;
    Integer measurementID = tevent.getMeasurementID();
    Integer localStatsID = tevent.getLocalStatsID();
    // TODO: Perhaps it would make sense to separate these two
    Long measurement = tevent.getAttSliceMillis() + tevent.getComputeEventMillis();

    // Get the correct row from the table
    Map<Integer, Long> localMeasurements = timings.row(measurementID);
    localMeasurements.put(localStatsID, measurement);

    if (localMeasurements.size() == numLocalStats) {
      DescriptiveStatistics rowStats = new DescriptiveStatistics();
      for (Long value : localMeasurements.values()) {
        rowStats.addValue(value);
      }
      double rowMean = rowStats.getMean();
      logger.info("Measurement: {} Avg slice millis: {}",
          measurementID, rowMean);
      logger.info("95%% slice millis: {}",
          rowStats.getPercentile(95));
      meanStats.addValue(rowMean);
      logger.info("Mean avg slice millis: {}",
          meanStats.getMean());
      // We are done with this measurement instance, so we can remove it
      timings.row(measurementID).clear();
    }
    // TODO: Handle case where we didn't manage to collect stats from all local stats processors at the end of the stream


    return true;
  }

  @Override
  public void onCreate(int id) {
    timings = HashBasedTable.create();
  }

  @Override
  public Processor newProcessor(Processor processor) {
    TimingsProcessor oldProcessor = (TimingsProcessor) processor;
    TimingsProcessor newProcessor = new TimingsProcessor(oldProcessor);

    newProcessor.setTimingStream(oldProcessor.getTimingStream());

    return newProcessor;
  }

  public Stream getTimingStream() {
    return timingStream;
  }

  public void setTimingStream(Stream timingStream) {
    this.timingStream = timingStream;
  }
}
