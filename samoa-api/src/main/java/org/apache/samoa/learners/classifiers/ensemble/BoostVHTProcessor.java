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

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.learners.classifiers.trees.ActiveLearningNode;
import org.apache.samoa.learners.classifiers.trees.LocalResultContentEvent;
import org.apache.samoa.moa.classifiers.core.splitcriteria.InfoGainSplitCriterion;
import org.apache.samoa.moa.classifiers.core.splitcriteria.SplitCriterion;
import org.apache.samoa.topology.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The Class BoostVHTProcessor.
 */
abstract public class BoostVHTProcessor implements Processor {

  private static final long serialVersionUID = -1550901409625192730L;
  private static final Logger logger = LoggerFactory.getLogger(BoostVHTProcessor.class);
  
  //--- they are configured from the user in BoostVHT
  protected SplitCriterion splitCriterion;
  
  protected Double splitConfidence;
  
  protected Double tieThreshold;
  
  protected int gracePeriod;
  
  protected int parallelismHint;
  
  protected int timeOut;

  protected ActiveLearningNode.SplittingOption splittingOption;
  
  //------
  

  /** The input dataset to BoostVHT. */
  protected Instances dataset;
  
  /** The ensemble size. */
  protected int ensembleSize;
  
  /** The result stream. */
  protected Stream resultStream;
  
  /** The control stream. */
  protected Stream controlStream;
  
  /** The attribute stream. */
  protected Stream attributeStream;
  
  protected BoostMAProcessor[] mAPEnsemble;

  /** Ramdom number generator. */
  protected Random random;

  protected int seed;

  
  protected double trainingWeightSeenByModel;

  protected int numberOfClasses;

  protected int maxBufferSize;

  private Map<Integer, Pair<Long, Long>> timings;
  private long instancesSeen = 0;
  private DescriptiveStatistics sliceOverallMeans;
  private DescriptiveStatistics computeOverallMeans;

  public BoostVHTProcessor() {}

  /**
   * On event.
   * 
   * @param event the event
   * @return true, if successful
   */
  public boolean process(ContentEvent event) {

    if (event instanceof InstanceContentEvent) {
      instancesSeen++;
      InstanceContentEvent inEvent = (InstanceContentEvent) event;
      //todo:: (Faye) check if any precondition is needed

      double[] combinedPrediction = predict(inEvent);
      this.resultStream.put(newResultContentEvent(combinedPrediction, inEvent));

      // estimate model parameters using the training data
      train(inEvent);
      if ((instancesSeen % 10_000 == 0 || inEvent.isLastEvent()) && !timings.isEmpty()) {
        DescriptiveStatistics sliceStats = new DescriptiveStatistics();
        DescriptiveStatistics computeStats = new DescriptiveStatistics();
        for (Pair<Long, Long> entry : timings.values()) {
          sliceStats.addValue(entry.getLeft());
          computeStats.addValue(entry.getRight());
        }

        logger.info("Avg slice millis: {}, avg compute millis: {}",
            sliceStats.getMean(),
            computeStats.getMean());
        logger.info("95%% slice millis: {}, 95%% compute millis: {}",
            sliceStats.getPercentile(95),
            computeStats.getPercentile(95));
        sliceOverallMeans.addValue(sliceStats.getMean());
        computeOverallMeans.addValue(computeStats.getMean());
        logger.info("Mean avg slice millis: {}, mean avg compute millis: {}",
            sliceOverallMeans.getMean(),
            computeOverallMeans.getMean());
      }
    } else if (event instanceof LocalResultContentEvent) {
      LocalResultContentEvent lrce = (LocalResultContentEvent) event;
      mAPEnsemble[lrce.getEnsembleId()].updateModel(lrce);
      timings.put(lrce.getLocalStatsId(), new ImmutablePair<>(lrce.getAttSliceMillis(), lrce.getComputeEventMillis()));
    }



    return true;
  }

  @Override
  public void onCreate(int id) {
    
    mAPEnsemble = new BoostMAProcessor[ensembleSize];

    random = new Random(seed);

//    this.nOfMsgPerEnseble = new long[ensembleSize];
    
    //----instantiate the MAs
    for (int i = 0; i < ensembleSize; i++) {
      BoostMAProcessor newProc = new BoostMAProcessor.Builder(dataset)
          .splitCriterion(splitCriterion)
          .splitConfidence(splitConfidence)
          .tieThreshold(tieThreshold)
          .gracePeriod(gracePeriod)
          .parallelismHint(parallelismHint)
          .timeOut(timeOut)
          .processorID(i) // The BoostMA processors get incremental ids
          .maxBufferSize(maxBufferSize)
          .splittingOption(splittingOption)
          .build();
      newProc.setAttributeStream(this.attributeStream);
      newProc.setControlStream(this.controlStream);
      mAPEnsemble[i] = newProc;
    }
    timings = new HashMap<>(parallelismHint);
    sliceOverallMeans = new DescriptiveStatistics();
    computeOverallMeans = new DescriptiveStatistics();
  }

  abstract protected double[] predict(InstanceContentEvent inEvent);

  abstract protected void train(InstanceContentEvent inEvent);

  /**
   * Helper method to generate new ResultContentEvent based on an instance and its prediction result.
   *
   * @param combinedPrediction
   *          The predicted class label from the Boost-VHT decision tree model.
   * @param inEvent
   *          The associated instance content event
   * @return ResultContentEvent to be sent into Evaluator PI or other destination PI.
   */
  private ResultContentEvent newResultContentEvent(double[] combinedPrediction, InstanceContentEvent inEvent) {
    ResultContentEvent rce = new ResultContentEvent(inEvent.getInstanceIndex(), inEvent.getInstance(),
            inEvent.getClassId(), combinedPrediction, inEvent.isLastEvent());
    rce.setEvaluationIndex(inEvent.getEvaluationIndex());
    return rce;
  }

  public static class BoostBuilder {
    // BoostVHT processor parameters
    protected Instances dataset;
    protected int ensembleSize;
    protected int numberOfClasses;

    // BoostMAProcessor parameters
    protected SplitCriterion splitCriterion = new InfoGainSplitCriterion();
    protected double splitConfidence;
    protected double tieThreshold;
    protected int gracePeriod;
    protected int parallelismHint;
    protected int timeOut = Integer.MAX_VALUE;
    protected ActiveLearningNode.SplittingOption splittingOption;
    protected int maxBufferSize;
    protected int seed;

    public BoostBuilder(Instances dataset) {
      this.dataset = dataset;
    }

    public BoostBuilder() {}

    public BoostBuilder(BoostVHTProcessor oldProcessor) {
      this.dataset = oldProcessor.getDataset();
      this.ensembleSize = oldProcessor.getEnsembleSize();
      this.numberOfClasses = oldProcessor.getNumberOfClasses();
      this.splitCriterion = oldProcessor.getSplitCriterion();
      this.splitConfidence = oldProcessor.getSplitConfidence();
      this.tieThreshold = oldProcessor.getTieThreshold();
      this.gracePeriod = oldProcessor.getGracePeriod();
      this.parallelismHint = oldProcessor.getParallelismHint();
      this.timeOut = oldProcessor.getTimeOut();
      this.splittingOption = oldProcessor.getSplittingOption();
      this.seed = oldProcessor.getSeed();
    }

    public BoostBuilder ensembleSize(int ensembleSize) {
      this.ensembleSize = ensembleSize;
      return this;
    }

    public BoostBuilder numberOfClasses(int numberOfClasses) {
      this.numberOfClasses = numberOfClasses;
      return this;
    }

    public BoostBuilder splitCriterion(SplitCriterion splitCriterion) {
      this.splitCriterion = splitCriterion;
      return this;
    }

    public BoostBuilder splitConfidence(double splitConfidence) {
      this.splitConfidence = splitConfidence;
      return this;
    }

    public BoostBuilder tieThreshold(double tieThreshold) {
      this.tieThreshold = tieThreshold;
      return this;
    }

    public BoostBuilder gracePeriod(int gracePeriod) {
      this.gracePeriod = gracePeriod;
      return this;
    }

    public BoostBuilder parallelismHint(int parallelismHint) {
      this.parallelismHint = parallelismHint;
      return this;
    }

    public BoostBuilder timeOut(int timeOut) {
      this.timeOut = timeOut;
      return this;
    }

    public BoostBuilder splittingOption(ActiveLearningNode.SplittingOption splittingOption) {
      this.splittingOption = splittingOption;
      return this;
    }

    public BoostBuilder maxBufferSize(int maxBufferSize) {
      this.maxBufferSize= maxBufferSize;
      return this;
    }

    public BoostBuilder seed(int seed) {
      this.seed = seed;
      return this;
    }
  }
  
  public Instances getInputInstances() {
    return dataset;
  }
  
  public void setInputInstances(Instances dataset) {
    this.dataset = dataset;
  }
  
  public Stream getResultStream() {
    return this.resultStream;
  }
  
  public void setResultStream(Stream resultStream) {
    this.resultStream = resultStream;
  }

  public int getEnsembleSize() {
    return ensembleSize;
  }

  public Stream getControlStream() {
    return controlStream;
  }
  
  public void setControlStream(Stream controlStream) {
    this.controlStream = controlStream;
  }

  public  Stream getAttributeStream() {
    return attributeStream;
  }

  public void setAttributeStream(Stream attributeStream) {
    this.attributeStream = attributeStream;
  }

  public SplitCriterion getSplitCriterion() {
    return splitCriterion;
  }


  public ActiveLearningNode.SplittingOption getSplittingOption() {
    return splittingOption;
  }

  public Double getSplitConfidence() {
    return splitConfidence;
  }
  

  public Double getTieThreshold() {
    return tieThreshold;
  }

  public int getSeed() {
    return seed;
  }

  public int getGracePeriod() {
    return gracePeriod;
  }
  

  public int getParallelismHint() {
    return parallelismHint;
  }

  public int getTimeOut() {
    return timeOut;
  }
  
  public void setTimeOut(int timeOut) {
    this.timeOut = timeOut;
  }
  
  public int getNumberOfClasses() {
    return numberOfClasses;
  }
  
  public void setNumberOfClasses(int numberOfClasses) {
    this.numberOfClasses = numberOfClasses;
  }
  
  public Instances getDataset() {
    return dataset;
  }
}
