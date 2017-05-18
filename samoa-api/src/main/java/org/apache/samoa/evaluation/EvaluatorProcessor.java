package org.apache.samoa.evaluation;

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.moa.core.Measurement;
import org.apache.samoa.moa.evaluation.LearningCurve;
import org.apache.samoa.moa.evaluation.LearningEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluatorProcessor implements Processor {

  /**
	 * 
	 */
  private static final long serialVersionUID = -2778051819116753612L;

  private static final Logger logger =
      LoggerFactory.getLogger(EvaluatorProcessor.class);

  private static final String ORDERING_MEASUREMENT_NAME = "evaluation instances";

  private final PerformanceEvaluator evaluator;
  private final int samplingFrequency;
  private final File dumpFile;
  private PrintStream immediateResultStream = null;
  private PrintStream metadataStream = null;//
  private boolean firstDump = true;

  private long totalCount = 0;
  private long experimentStart = 0;


  
  long sampleDuration = 0;
  private  File metrics;
  long sampleDurationInNanoseconds = 0;
  long sampleEndCPUTime = 0;
//  long sampleCPUTime =0;
//  long startCPUTime = 0;
//  ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
  
  private long sampleStart = 0;

  private LearningCurve learningCurve;
  private int id;

  private EvaluatorProcessor(Builder builder) {
    this.evaluator = builder.evaluator;
    this.samplingFrequency = builder.samplingFrequency;
    this.dumpFile = builder.dumpFile;
  }

  @Override
  public boolean process(ContentEvent event) {

    ResultContentEvent result = (ResultContentEvent) event;

    if ((totalCount > 0) && (totalCount % samplingFrequency) == 0) {
      long sampleEnd = System.nanoTime();
      sampleDuration = TimeUnit.MILLISECONDS.convert(sampleEnd - sampleStart, TimeUnit.NANOSECONDS);
      sampleDurationInNanoseconds = sampleEnd - sampleStart;
      sampleStart = sampleEnd;
      logger.info("{} milliseconds for {} instances", sampleDuration, samplingFrequency);
      this.addMeasurement();
    }

    if (result.isLastEvent()) {
      this.concludeMeasurement();
      return true;
    }

    evaluator.addResult(result.getInstance(), result.getClassVotes());
    totalCount += 1;

    if (totalCount == 1) {
      sampleStart = System.nanoTime();
    }

    return false;
  }

  @Override
  public void onCreate(int id) {
    this.id = id;
    this.learningCurve = new LearningCurve(ORDERING_MEASUREMENT_NAME);
  
    try {
      String datafile = "/Users/fobeligi/Documents/GBDT/experiments-output-310317/forestCoverType/forestCoverType";
      metrics = new File(datafile+"_evaluatorMetrics.csv");
      this.metadataStream = new PrintStream(
                new FileOutputStream(metrics), true);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  
    if (this.dumpFile != null) {
      try {
        if (dumpFile.exists()) {
          this.immediateResultStream = new PrintStream(
              new FileOutputStream(dumpFile, true), true);
        } else {
          this.immediateResultStream = new PrintStream(
              new FileOutputStream(dumpFile), true);
        }

      } catch (FileNotFoundException e) {
        this.immediateResultStream = null;
        logger.error("File not found exception for {}:{}", this.dumpFile.getAbsolutePath(), e.toString());

      } catch (Exception e) {
        this.immediateResultStream = null;
        logger.error("Exception when creating {}:{}", this.dumpFile.getAbsolutePath(), e.toString());
      }
    }

    this.firstDump = true;
  }

  @Override
  public Processor newProcessor(Processor p) {
    EvaluatorProcessor originalProcessor = (EvaluatorProcessor) p;
    EvaluatorProcessor newProcessor = new EvaluatorProcessor.Builder(originalProcessor).build();

    if (originalProcessor.learningCurve != null) {
      newProcessor.learningCurve = originalProcessor.learningCurve;
    }

    return newProcessor;
  }

  @Override
  public String toString() {
    StringBuilder report = new StringBuilder();

    report.append(EvaluatorProcessor.class.getCanonicalName());
    report.append("id = ").append(this.id);
    report.append('\n');

    if (learningCurve.numEntries() > 0) {
      report.append(learningCurve.toString());
      report.append('\n');
    }
    return report.toString();
  }

  private void addMeasurement() {
    List<Measurement> measurements = new Vector<>();
    measurements.add(new Measurement(ORDERING_MEASUREMENT_NAME, totalCount));

    Collections.addAll(measurements, evaluator.getPerformanceMeasurements());

    Measurement[] finalMeasurements = measurements.toArray(new Measurement[measurements.size()]);

    LearningEvaluation learningEvaluation = new LearningEvaluation(finalMeasurements);
    learningCurve.insertEntry(learningEvaluation);
    logger.debug("evaluator id = {}", this.id);
    logger.info(learningEvaluation.toString());

    if (immediateResultStream != null) {
      if (firstDump) {
        immediateResultStream.println(learningCurve.headerToString());
//        metadataStream.println("#Instances classified, Experiment's Wall clock ");
        firstDump = false;
      }

      immediateResultStream.println(learningCurve.entryToString(learningCurve.numEntries() - 1));
      immediateResultStream.flush();

//      metadataStream.println(totalCount + "," +sampleDurationInNanoseconds );
    }
  }

  private void concludeMeasurement() {
    logger.info("last event is received!");
    logger.info("total count: {}", this.totalCount);

    String learningCurveSummary = this.toString();
    logger.info(learningCurveSummary);

    long experimentEnd = System.nanoTime();
    long totalExperimentTime = TimeUnit.MILLISECONDS.convert(experimentEnd - experimentStart, TimeUnit.NANOSECONDS);
    logger.info("total evaluation time: {} milliseconds for {} instances", totalExperimentTime, totalCount);

    if (immediateResultStream != null) {
      immediateResultStream.println("# COMPLETED");
      immediateResultStream.flush();
    }
     logger.info("average throughput rate: {} instances/seconds",
     (totalCount/totalExperimentTime));
  }

  public static class Builder {

    private final PerformanceEvaluator evaluator;
    private int samplingFrequency = 100000;
    private File dumpFile = null;

    public Builder(PerformanceEvaluator evaluator) {
      this.evaluator = evaluator;
    }

    public Builder(EvaluatorProcessor oldProcessor) {
      this.evaluator = oldProcessor.evaluator;
      this.samplingFrequency = oldProcessor.samplingFrequency;
      this.dumpFile = oldProcessor.dumpFile;
    }

    public Builder samplingFrequency(int samplingFrequency) {
      this.samplingFrequency = samplingFrequency;
      return this;
    }

    public Builder dumpFile(File file) {
      this.dumpFile = file;
      return this;
    }

    public EvaluatorProcessor build() {
      return new EvaluatorProcessor(this);
    }
  }
}
