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

import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.moa.classifiers.core.splitcriteria.SplitCriterion;
import org.apache.samoa.moa.core.MiscUtils;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * The Class BoostVHTProcessor.
 */
public class BoostVHTProcessor implements Processor {

  private static final long serialVersionUID = -1550901409625192730L;
  private static final Logger logger = LoggerFactory.getLogger(BoostVHTProcessor.class);
  
  //--- they are configured from the user in BoostVHT
  private SplitCriterion splitCriterion;
  
  private Double splitConfidence;
  
  private Double tieThreshold;
  
  private int gracePeriod;
  
  private int parallelismHint;
  
  private int timeOut;
  
  //------
  
  /** The builder. */
  private TopologyBuilder builder;
  
  /** The input dataset to BoostVHT. */
  private Instances dataset;
  
  /** The ensemble size. */
  private int ensembleSize;
  
  /** The result stream. */
  private Stream resultStream;
  
  /** The control stream. */
  private Stream controlStream;
  
  /** The attribute stream. */
  private Stream attributeStream;
  
//  /** The input streams of each MA (of the ensemble). */
//  private Stream ensembleInputStream; //todo:: check if needed
  
  protected BoostMAProcessor[] mAPEnsemble;

  /** Ramdom number generator. */
  protected Random random = new Random(); //TODO make random seed configurable

  private BoostVHTProcessor(Builder builder) {
    this.dataset = builder.dataset;
  }

  /**
   * On event.
   * 
   * @param event the event
   * @return true, if successful
   */
  public boolean process(ContentEvent event) {
   //todo:: check if any precondition is needed
    InstanceContentEvent inEvent = (InstanceContentEvent) event;

//    if (inEvent.getInstanceIndex() < 0) {
//      // end learning
//      for (Stream stream : ensembleStreams)
//        stream.put(event);
//      return false;
//    }

    if (inEvent.isTesting()) {
      Instance testInstance = inEvent.getInstance();
      double[][] predictionsPerEnsemble = new double[ensembleSize][];

      for (int i = 0; i < ensembleSize; i++) {
        Instance instanceCopy = testInstance.copy();
        InstanceContentEvent instanceContentEvent = new InstanceContentEvent(inEvent.getInstanceIndex(), instanceCopy,
            false, true);
        instanceContentEvent.setClassifierIndex(i); //TODO probably not needed anymore
        instanceContentEvent.setEvaluationIndex(inEvent.getEvaluationIndex()); //TODO probably not needed anymore
  
        predictionsPerEnsemble[i] = mAPEnsemble[i].getVotesForInstance(testInstance);
      }
      computeBoosting(predictionsPerEnsemble);
    }

    // estimate model parameters using the training data
    if (inEvent.isTraining()) {
      train(inEvent);
    }
    return true;
  }
  
  /**
   * Train.
   * 
   * @param inEvent
   *          the in event
   */
  protected void train(InstanceContentEvent inEvent) {
    Instance trainInstance = inEvent.getInstance();
    for (int i = 0; i < ensembleSize; i++) {
      int k = MiscUtils.poisson(1.0, this.random);
      if (k > 0) {
        Instance weightedInstance = trainInstance.copy();
        weightedInstance.setWeight(trainInstance.weight() * k);
        InstanceContentEvent instanceContentEvent = new InstanceContentEvent(inEvent.getInstanceIndex(),
            weightedInstance, true, false);
        instanceContentEvent.setClassifierIndex(i);
        instanceContentEvent.setEvaluationIndex(inEvent.getEvaluationIndex());
      }
    }
  }

  @Override
  public void onCreate(int id) {
//
//    mAPEnsemble = new BoostMAProcessor[ensembleSize];
////    subResultStreams = new Stream[ensembleSize];
//
//    //----instantiate the rest of the MAs
    for (int i = 0; i < ensembleSize; i++) {
      //todo::  what dataset should we pass in each MA that we instantiate?
      mAPEnsemble[i] = new BoostMAProcessor.Builder(dataset)
//              .splitCriterion(splitCriterion)
//              .splitConfidence(splitConfidence)
//              .tieThreshold(tieThreshold)
//              .gracePeriod(gracePeriod)
//              .timeOut(timeOut)
          .parallelismHint(parallelismHint)
          .setBoostProcessor(this)
          .build();

      //todo:: check if the below is needed. Should we add each MA in the topology?
//      this.builder.addProcessor(modelEnsemble[i], 1); //modelAggregatorParallelism = 1
    }

  }
  
  // todo:: use also the boosting algo and the training weight for each model to compute the final result and put it to the resultStream
  private void computeBoosting(double[][] predictionsPerEnsemble) {
    
  }

  public static class Builder {
    // required parameters
    private final Instances dataset;

    private int ensembleSize;

    public Builder(Instances dataset) {
      this.dataset = dataset;
    }

    public Builder(BoostVHTProcessor vhtProcessor) {
      this.dataset = vhtProcessor.dataset;
    }

    public Builder setEnsembleSize(int ensembleSize) {
      this.ensembleSize = ensembleSize;
      return this;
    }

    public BoostVHTProcessor build() {
      return new BoostVHTProcessor(this);
    }
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

  public void setEnsembleSize(int ensembleSize) {
    this.ensembleSize = ensembleSize;
  }

  public Stream getControlStream() {
    return controlStream;
  }

  public void setControlStream(Stream controlStream) {
    this.controlStream = controlStream;
  }

  public Stream getAttributeStream() {
    return attributeStream;
  }

  public void setAttributeStream(Stream attributeStreams) {
    this.attributeStream = attributeStreams;
  }
//
//  public TopologyBuilder getBuilder() {
//    return builder;
//  }
//
//  public void setBuilder(TopologyBuilder builder) {
//    this.builder = builder;
//  }
//
//  public SplitCriterion getSplitCriterion() {
//    return splitCriterion;
//  }
//
//  public void setSplitCriterion(SplitCriterion splitCriterion) {
//    this.splitCriterion = splitCriterion;
//  }
//
//  public Double getSplitConfidence() {
//    return splitConfidence;
//  }
//
//  public void setSplitConfidence(Double splitConfidence) {
//    this.splitConfidence = splitConfidence;
//  }
//
//  public Double getTieThreshold() {
//    return tieThreshold;
//  }
//
//  public void setTieThreshold(Double tieThreshold) {
//    this.tieThreshold = tieThreshold;
//  }
//
//  public int getGracePeriod() {
//    return gracePeriod;
//  }
//
//  public void setGracePeriod(int gracePeriod) {
//    this.gracePeriod = gracePeriod;
//  }
//
//  public int getParallelismHint() {
//    return parallelismHint;
//  }
//
//  public void setParallelismHint(int parallelismHint) {
//    this.parallelismHint = parallelismHint;
//  }
//
//  public int getTimeOut() {
//    return timeOut;
//  }
//
//  public void setTimeOut(int timeOut) {
//    this.timeOut = timeOut;
//  }
  
  @Override
  public Processor newProcessor(Processor sourceProcessor) {
    BoostVHTProcessor originProcessor = (BoostVHTProcessor) sourceProcessor;
    BoostVHTProcessor newProcessor = new BoostVHTProcessor.Builder(originProcessor).build();
    if (originProcessor.getResultStream() != null) {
      newProcessor.setResultStream(originProcessor.getResultStream());
    }
    newProcessor.setEnsembleSize(originProcessor.getEnsembleSize());
    /*
     * if (originProcessor.getLearningCurve() != null){
     * newProcessor.setLearningCurve((LearningCurve)
     * originProcessor.getLearningCurve().copy()); }
     */
    return newProcessor;
  }
}
