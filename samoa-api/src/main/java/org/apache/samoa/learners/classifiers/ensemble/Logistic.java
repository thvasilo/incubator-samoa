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

import org.apache.commons.math3.util.FastMath;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.instances.Utils;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.moa.core.DoubleVector;

import java.util.Arrays;

public class Logistic extends BoostVHTProcessor {

  private static final long serialVersionUID = -8623148029719610033L;
  private double[] alpha;

  private Logistic(LogisticBoostBuilder boostBuilder) {
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
  protected double[] predict(InstanceContentEvent inEvent) {
//    Instance testInstance = inEvent.getInstance();
//    double finalPrediction = 0;
//
//    for (int i = 0; i < ensembleSize; i++) {
//      double prediction = Utils.maxIndex(mAPEnsemble[i].getVotesForInstance(testInstance)) == 0 ? -1d : 1d;
//      finalPrediction += prediction * alpha[i];
//    }
//
//    if (finalPrediction > 0) {
//      return new double[] {0, 1};
//    } else {
//      return new double[] {1, 0};
//    }

    Instance testInstance = inEvent.getInstance();
    DoubleVector combinedPredictions = new DoubleVector();

    for (int i = 0; i < ensembleSize; i++) {
      double memberWeight = alpha[i];
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

  @Override
  protected void train(InstanceContentEvent inEvent) {
    double s = 0;
    Instance trainInstance = inEvent.getInstance();

    trainingWeightSeenByModel++;
    double eta = 4.0 / FastMath.sqrt(trainingWeightSeenByModel);

    for (int i = 0; i < ensembleSize; i++) {
      double weight = 1 / (1 + FastMath.exp(s));

      // Put label and prediction in the -1/1 domain
      int wlPrediction = Utils.maxIndex(mAPEnsemble[i].getVotesForInstance(trainInstance)) == 0 ? -1 : 1;
      int label = (int) trainInstance.classValue() == 0 ? -1 : 1;

      double z = label * wlPrediction;

      s += z * alpha[i];

      // Update alpha
      alpha[i] = eta * z / (1 + FastMath.exp(s));
      if (alpha[i] > 2) {
        alpha[i] = 2;
      }
      if (alpha[i] < -2) {
        alpha[i] = -2;
      }

      if (weight > 0) {
        Instance weightedInstance = trainInstance.copy();
        weightedInstance.setWeight(trainInstance.weight() * weight);
        mAPEnsemble[i].trainOnInstance(weightedInstance);
      }
    }


  }

  @Override
  public void onCreate(int id) {
    super.onCreate(id);
    alpha = new double[ensembleSize];
    Arrays.fill(alpha, 0.0d);
  }

  @Override
  public Processor newProcessor(Processor processor) {
    Logistic originalProcessor = (Logistic) processor;
    Logistic newProcessor = new LogisticBoostBuilder(originalProcessor).build();
    // TODO(tvas): Why are the streams handled separately from the rest of the options? Why not handle everything in the
    // copy BoostBuilder?
    if (originalProcessor.getResultStream() != null) {
      newProcessor.setResultStream(originalProcessor.getResultStream());
      newProcessor.setControlStream(originalProcessor.getControlStream());
      newProcessor.setAttributeStream(originalProcessor.getAttributeStream());
    }
    return newProcessor;
  }

  public static class LogisticBoostBuilder extends BoostBuilder {

    public LogisticBoostBuilder(Logistic oldProcessor) {
      super(oldProcessor);
    }

    public LogisticBoostBuilder(BoostBuilder oldBoostBuilder) {
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


    public Logistic build() {
      return new Logistic(this);
    }
  }
}
