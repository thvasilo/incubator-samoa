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
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.instances.Utils;
import org.apache.samoa.learners.InstanceContentEvent;

public class OnlineBBM extends BoostVHTProcessor {

  private static final long serialVersionUID = 2084624183026066689L;
  private Table<Integer, Integer, Long> binomialMemory;

  private double gamma = 0.1;

  private OnlineBBM(OnlineBBMBuilder boostBuilder) {
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
  protected double[] predict(InstanceContentEvent inEvent) {
    Instance testInstance = inEvent.getInstance();
    double finalPrediction = 0;

    for (int i = 0; i < ensembleSize; i++) {
      double prediction = Utils.maxIndex(mAPEnsemble[i].getVotesForInstance(testInstance)) == 0 ? -1d : 1d;
      finalPrediction += prediction;
    }

    if (finalPrediction > 0) {
      return new double[] {0, 1};
    } else {
      return new double[] {1, 0};
    }
  }

  @Override
  protected void train(InstanceContentEvent inEvent) {
    // TODO: The algorithm assumes a label domain of -1/1. Will need to take that into consideration
    double s = 0;
    Instance trainInstance = inEvent.getInstance();
    this.trainingWeightSeenByModel++;
    for (int i = 0; i < ensembleSize; i++) {
      int k = (int) FastMath.floor((ensembleSize - i - s) / 2);
      long c;
      if (ensembleSize - (i + 1) < 0) {
        c = 0L;
      }
      else if (k > ensembleSize - ( i +1 )) {
        c = 0L;
      }
      else if (k < 0) {
        c = 0L;
      }
      else if (binomialMemory.contains(ensembleSize - (i + 1), k))
      {
        c = binomialMemory.get(ensembleSize - (i + 1), k);
      }
      else {
        c = CombinatoricsUtils.binomialCoefficient(ensembleSize - (i + 1), k);
        binomialMemory.put(ensembleSize - (i + 1), k, c);
      }
      double weight = c * FastMath.pow(0.5 + gamma, k) *
          FastMath.pow(0.5 - gamma, ensembleSize - (i + 1) - k);

      // Put label and prediction in the -1/1 domain
      int wlPrediction = Utils.maxIndex(mAPEnsemble[i].getVotesForInstance(trainInstance)) == 0 ? -1 : 1;
      int label = (int) trainInstance.classValue() == 0 ? -1 : 1;

      s += label * wlPrediction;

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
    binomialMemory = HashBasedTable.create(ensembleSize, ensembleSize);
  }

  @Override
  public Processor newProcessor(Processor processor) {
    OnlineBBM originalProcessor = (OnlineBBM) processor;
    OnlineBBM newProcessor = new OnlineBBMBuilder(originalProcessor).build();
    // TODO(tvas): Why are the streams handled separately from the rest of the options? Why not handle everything in the
    // copy BoostBuilder?
    if (originalProcessor.getResultStream() != null) {
      newProcessor.setResultStream(originalProcessor.getResultStream());
      newProcessor.setControlStream(originalProcessor.getControlStream());
      newProcessor.setAttributeStream(originalProcessor.getAttributeStream());
    }
    return newProcessor;
  }

  public static class OnlineBBMBuilder extends BoostBuilder {
    double gamma = 0.1;

    public OnlineBBMBuilder(OnlineBBM oldProcessor) {
      super(oldProcessor);
    }

    public OnlineBBMBuilder(BoostBuilder oldBoostBuilder) {
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

    public OnlineBBMBuilder gamma(double gamma) {
      this.gamma = gamma;
      return this;
    }

    public OnlineBBM build() {
      return new OnlineBBM(this);
    }
  }
}
