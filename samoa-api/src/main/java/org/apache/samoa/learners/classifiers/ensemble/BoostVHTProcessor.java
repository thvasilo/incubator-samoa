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
import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.moa.classifiers.core.splitcriteria.SplitCriterion;
import org.apache.samoa.moa.core.DoubleVector;
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
  
  protected BoostMAProcessor[] mAPEnsemble;

  /** Ramdom number generator. */
  protected Random random = new Random(); //TODO make random seed configurable

  //-----
  // Weigths classifier
  protected double[] scms;
  
  // Weights instance
  protected double[] swms;
  
  protected double trainingWeightSeenByModel; //todo:: (Faye) when is this updated?
  //-----
  
  
  //---for SAMME
  private int numOfClasses;
  //---

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
   
    InstanceContentEvent inEvent = (InstanceContentEvent) event;
      //todo:: (Faye) check if any precondition is needed
//    if (inEvent.getInstanceIndex() < 0) {
//      end learning
//      for (Stream stream : ensembleStreams)
//        stream.put(event);
//      return false;
//    }
    
    if (inEvent.isTesting()) {
      double[] combinedPrediction = computeBoosting(inEvent);
      this.resultStream.put(newResultContentEvent(combinedPrediction,inEvent));
    }

    // estimate model parameters using the training data
    if (inEvent.isTraining()) {
      train(inEvent);
    }
    return true;
  }

  @Override
  public void onCreate(int id) {
    
    mAPEnsemble = new BoostMAProcessor[ensembleSize];

    this.scms = new double[ensembleSize];
    this.swms = new double[ensembleSize];
    
    //----instantiate the MAs
    for (int i = 0; i < ensembleSize; i++) {
      //todo::  (Faye) what dataset should we pass in each MA that we instantiate? --> Ans: The same
      mAPEnsemble[i] = new BoostMAProcessor.Builder(dataset)
//              .splitCriterion(splitCriterion)
//              .splitConfidence(splitConfidence)
//              .tieThreshold(tieThreshold)
//              .gracePeriod(gracePeriod)
//              .parallelismHint(parallelismHint)
//              .timeOut(timeOut)
              .setBoostProcessor(this)
              .build();
    }
    
  }
  
  // todo:: (Faye) use also the boosting algo and the training weight for each model to compute the final result and put it to the resultStream
  private double[] computeBoosting(InstanceContentEvent inEvent) {
    
    Instance testInstance = inEvent.getInstance();
    DoubleVector combinedPredictions = new DoubleVector();
  
    for (int i = 0; i < ensembleSize; i++) {
      double[] predictionsPerEnsemble = mAPEnsemble[i].getVotesForInstance(testInstance);
      double memberWeight = getEnsembleMemberWeight(i);
      if (memberWeight > 0.0) {
        DoubleVector vote = new DoubleVector(predictionsPerEnsemble);
        if (vote.sumOfValues() > 0.0) {
          vote.normalize();
          vote.scaleValues(memberWeight);
          combinedPredictions.addValues(vote);
        }
      } else {
        break;
      }
    }
    return combinedPredictions.getArrayRef();
  }
  
  /**
   * Train.
   *
   * @param inEvent
   *          the in event
   */
  protected void train(InstanceContentEvent inEvent) {
    Instance trainInstance = inEvent.getInstance();
    
    double lambda_d = 1.0; //set the example's weight
    
    for (int i = 0; i < ensembleSize; i++) { //for each base model
      int k = MiscUtils.poisson(1.0, this.random); //set k according to poisson
      
      Instance weightedInstance = trainInstance.copy();
      if (k > 0) {
        weightedInstance.setWeight(trainInstance.weight() * k);
        InstanceContentEvent instanceContentEvent = new InstanceContentEvent(inEvent.getInstanceIndex(), weightedInstance, true, false);
        instanceContentEvent.setClassifierIndex(i);
        instanceContentEvent.setEvaluationIndex(inEvent.getEvaluationIndex());
        
        mAPEnsemble[i].process(instanceContentEvent);
      }
      //get prediction for the instance from the specific learner of the ensemble
      double[] prediction = mAPEnsemble[i].getVotesForInstance(trainInstance);
      
      //correctlyClassifies method of BoostMAProcessor
      if (mAPEnsemble[i].correctlyClassifies(trainInstance,prediction)) {
        this.trainingWeightSeenByModel = this.mAPEnsemble[i].getWeightSeenByModel();
        this.scms[i] += lambda_d;
        lambda_d *= this.trainingWeightSeenByModel / (2 * this.scms[i]);
      } else {
        this.swms[i] += lambda_d;
        lambda_d *= this.trainingWeightSeenByModel / (2 * this.swms[i]);
      }
    }
  }
  
  private double getEnsembleMemberWeight(int i) {
    double em = this.swms[i] / (this.scms[i] + this.swms[i]);
//    if ((em == 0.0) || (em > 0.5)) {
    if ((em == 0.0) || (em > (1.0 - 1.0/this.numOfClasses))) { //for SAMME
      return 0.0;
    }
    double Bm = em / (1.0 - em);
//    return Math.log(1.0 / Bm);
    return Math.log(1.0 / Bm ) + Math.log(this.numOfClasses - 1); //for SAMME
  }
  
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
  
  public TopologyBuilder getBuilder() {
    return builder;
  }
  
  public void setBuilder(TopologyBuilder builder) {
    this.builder = builder;
  }
  
  public SplitCriterion getSplitCriterion() {
    return splitCriterion;
  }
  
  public void setSplitCriterion(SplitCriterion splitCriterion) {
    this.splitCriterion = splitCriterion;
  }
  
  public Double getSplitConfidence() {
    return splitConfidence;
  }
  
  public void setSplitConfidence(Double splitConfidence) {
    this.splitConfidence = splitConfidence;
  }
  
  public Double getTieThreshold() {
    return tieThreshold;
  }
  
  public void setTieThreshold(Double tieThreshold) {
    this.tieThreshold = tieThreshold;
  }
  
  public int getGracePeriod() {
    return gracePeriod;
  }
  
  public void setGracePeriod(int gracePeriod) {
    this.gracePeriod = gracePeriod;
  }
  
  public int getParallelismHint() {
    return parallelismHint;
  }
  
  public void setParallelismHint(int parallelismHint) {
    this.parallelismHint = parallelismHint;
  }
  
  public int getTimeOut() {
    return timeOut;
  }
  
  public void setTimeOut(int timeOut) {
    this.timeOut = timeOut;
  }
  
  public int getNumOfClasses() {
    return numOfClasses;
  }
  
  public void setNumOfClasses(int numOfClasses) {
    this.numOfClasses = numOfClasses;
  }
  
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
