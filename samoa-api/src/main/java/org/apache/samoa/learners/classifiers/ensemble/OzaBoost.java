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

import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.moa.core.MiscUtils;

public class OzaBoost extends BoostVHTProcessor {

  private static final long serialVersionUID = -254518562711094021L;

  //-----
  // lambda_m correct
  protected double[] scms;

  // lambda_m wrong
  protected double[] swms;

  private OzaBoost(OzaBoostBuilder boostBuilder) {
    // This is bug prone, there should be a better way to do (code duplicated in OzaBoostBuilder)
    this.dataset = boostBuilder.dataset;
    this.ensembleSize = boostBuilder.ensembleSize;
    this.seed = boostBuilder.seed;
    this.numberOfClasses = boostBuilder.numberOfClasses;
    this.splitCriterion = boostBuilder.splitCriterion;
    this.splitConfidence = boostBuilder.splitConfidence;
    this.tieThreshold = boostBuilder.tieThreshold;
    this.gracePeriod = boostBuilder.gracePeriod;
    this.parallelismHint = boostBuilder.parallelismHint;
    this.timeOut = boostBuilder.timeOut;
    this.splittingOption = boostBuilder.splittingOption;
    this.maxBufferSize = boostBuilder.maxBufferSize;
  }

  @Override
  public Processor newProcessor(Processor sourceProcessor) {
    OzaBoost originalProcessor = (OzaBoost) sourceProcessor;
    OzaBoost newProcessor = new OzaBoostBuilder(originalProcessor).build();
    // TODO(tvas): Why are the streams handled separately from the rest of the options? Why not handle everything in the
    // copy BoostBuilder?
    if (originalProcessor.getResultStream() != null) {
      newProcessor.setResultStream(originalProcessor.getResultStream());
      newProcessor.setControlStream(originalProcessor.getControlStream());
      newProcessor.setAttributeStream(originalProcessor.getAttributeStream());
    }
    return newProcessor;
  }

  /**
   * Train.
   *
   * @param inEvent
   *          the in event
   */
  @Override
  protected void train(InstanceContentEvent inEvent) {
    Instance trainInstance = inEvent.getInstance();

    this.trainingWeightSeenByModel++;
    double lambda_d = 1.0;

    for (int i = 0; i < ensembleSize; i++) { //for each base model
      int k = MiscUtils.poisson(lambda_d, this.random); //set k according to poisson

      if (k > 0) {
        Instance weightedInstance = trainInstance.copy();
        weightedInstance.setWeight(trainInstance.weight() * k);
        mAPEnsemble[i].trainOnInstance(weightedInstance);
      }
      //get prediction for the instance from the specific learner of the ensemble
      double[] prediction = mAPEnsemble[i].getVotesForInstance(trainInstance);

      //correctlyClassifies method of BoostMAProcessor
      if (mAPEnsemble[i].correctlyClassifies(trainInstance,prediction)) {
        this.scms[i] += lambda_d;
        lambda_d *= this.trainingWeightSeenByModel / (2 * this.scms[i]);
      } else {
        this.swms[i] += lambda_d;
        lambda_d *= this.trainingWeightSeenByModel / (2 * this.swms[i]);
      }
    }
  }

  @Override
  protected double[] predict(InstanceContentEvent inEvent) {

    Instance testInstance = inEvent.getInstance();
    DoubleVector combinedPredictions = new DoubleVector();

    for (int i = 0; i < ensembleSize; i++) {
      double memberWeight = getEnsembleMemberWeight(i);
      if (memberWeight > 0.0) {
        DoubleVector vote = new DoubleVector(mAPEnsemble[i].getVotesForInstance(testInstance));
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

  private double getEnsembleMemberWeight(int i) {
    double em = this.swms[i] / (this.scms[i] + this.swms[i]);
//    if ((em == 0.0) || (em > 0.5)) {
    if ((em == 0.0) || (em > (1.0 - 1.0/this.numberOfClasses))) { //for SAMME
      return 0.0;
    }
    double Bm = em / (1.0 - em);
//    return Math.log(1.0 / Bm);
    return Math.log(1.0 / Bm ) + Math.log(this.numberOfClasses - 1); //for SAMME
  }

  public static class OzaBoostBuilder extends BoostBuilder {

    public OzaBoostBuilder(OzaBoost oldProcessor) {
      super(oldProcessor);
    }

    public OzaBoostBuilder(BoostBuilder oldBoostBuilder) {
      this.dataset = oldBoostBuilder.dataset;
      this.ensembleSize = oldBoostBuilder.ensembleSize;
      this.numberOfClasses = oldBoostBuilder.numberOfClasses;
      this.splitCriterion = oldBoostBuilder.splitCriterion;
      this.splitConfidence = oldBoostBuilder.splitConfidence;
      this.tieThreshold = oldBoostBuilder.tieThreshold;
      this.gracePeriod = oldBoostBuilder.gracePeriod;
      this.parallelismHint = oldBoostBuilder.parallelismHint;
      this.timeOut = oldBoostBuilder.timeOut;
      this.splittingOption = oldBoostBuilder.splittingOption;
      this.seed = oldBoostBuilder.seed;
    }

    public OzaBoost build() {
      return new OzaBoost(this);
    }
  }

  @Override
  public void onCreate(int id) {
    super.onCreate(id);
    this.scms = new double[ensembleSize];
    this.swms = new double[ensembleSize];
  }
}
