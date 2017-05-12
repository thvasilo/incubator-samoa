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

public class OSBoost extends BoostVHTProcessor {
  private static final long serialVersionUID = -7677780709403646166L;

  private double[] alpha;
  private double gamma;
  private double theta;

  private OSBoost (OSBoostBuilder boostBuilder) {
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

    this.gamma = boostBuilder.gamma;
  }

  @Override
  public Processor newProcessor(Processor processor) {
    OSBoost originalProcessor = (OSBoost) processor;
    OSBoost newProcessor = new OSBoostBuilder(originalProcessor).build();
    if (originalProcessor.getResultStream() != null) {
      newProcessor.setResultStream(originalProcessor.getResultStream());
      newProcessor.setControlStream(originalProcessor.getControlStream());
      newProcessor.setAttributeStream(originalProcessor.getAttributeStream());
    }
    return newProcessor;
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
    Instance trainInstance = inEvent.getInstance();

    trainingWeightSeenByModel++;
    double z = 0;
    double weight = 1.0;
    for (int i = 0; i < ensembleSize; i++) {
      // Put label and prediction in the -1/1 domain
      double wlPrediction = normalizedPrediction(mAPEnsemble[i].getVotesForInstance(trainInstance));
      assert ((int) trainInstance.classValue() == 0 || (int) trainInstance.classValue() == 1);
      double label = (int) trainInstance.classValue() == 0 ? -1 : 1;
      z += wlPrediction * label - theta;
      if (weight > 0) {
        Instance weightedInstance = trainInstance.copy();
        weightedInstance.setWeight(trainInstance.weight() * weight);
        mAPEnsemble[i].trainOnInstance(weightedInstance);
      }
      if (z > 0) {
        weight = FastMath.pow(1.0 - gamma, z / 2.0);
      } else {
        weight = 1.0;
      }
      assert weight <= 1.0;
    }
  }

  private double normalizedPrediction(double[] origVotes) {
    DoubleVector vote = new DoubleVector(origVotes);
    vote.normalize();
    return vote.getValue(1) - vote.getValue(0);
  }

  @Override
  public void onCreate(int id) {
    super.onCreate(id);
    alpha = new double[ensembleSize];
    Arrays.fill(alpha, 1d / ensembleSize);
    theta = gamma / (2 + gamma);
  }

  public static class OSBoostBuilder extends BoostBuilder {
    double gamma = 0.1;

    public OSBoostBuilder(OSBoost oldProcessor) {
      super(oldProcessor);
    }

    public OSBoostBuilder(BoostBuilder oldBoostBuilder) {
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

    public OSBoostBuilder gamma(double gamma) {
      this.gamma = gamma;
      return this;
    }

    public OSBoost build() {
      return new OSBoost(this);
    }
  }

}
