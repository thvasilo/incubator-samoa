package org.apache.samoa.learners.classifiers.ensemble.boosting;
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

import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.classifiers.LocalLearner;
import org.apache.samoa.moa.core.MiscUtils;

import java.util.Random;

import static org.apache.samoa.instances.Utils.maxIndex;

/**
 * The OzaBoost algorithm, as shown in the continuation paper [1].
 *
 * [1] Oza, Nikunj C. "Online bagging and boosting." Systems, man and cybernetics, 2005 IEEE International
 * conference on. Vol. 3. IEEE, 2005.
 */
public class OzaBoost implements BoostingModel {
  private static final long serialVersionUID = -4360335604627806863L;
  private static final Random random = new Random(1); // TODO: Properly initialize seed
  private final int ensembleSize;
  private double[] lambda_wrong;
  private double[] lambda_correct;
  private double[] epsilon;
  private double lambda;
  private double trainingWeightSeenByModel;

  public OzaBoost(int ensembleSize) {
    this.ensembleSize = ensembleSize;
    lambda = 1.0;
    trainingWeightSeenByModel = 0.0;
    lambda_correct = new double[ensembleSize];
    lambda_wrong = new double[ensembleSize];
    epsilon = new double[ensembleSize];
  }

  @Override
  public BoostingModel createCopy() {
    OzaBoost copy = new OzaBoost(this.ensembleSize);
    System.arraycopy(this.lambda_correct, 0, copy.lambda_correct, 0, this.ensembleSize);
    System.arraycopy(this.lambda_wrong, 0, copy.lambda_wrong, 0, this.ensembleSize);
    System.arraycopy(this.epsilon, 0, copy.epsilon, 0, this.ensembleSize);
    copy.lambda = this.lambda;
    copy.trainingWeightSeenByModel= this.trainingWeightSeenByModel;
    return copy;
  }

  @Override
  public double[] predict(InstanceContent instance, DoubleVector weakPredictionsSum) {
    return new double[0];
  }

  @Override
  public void weighPredictions(int learnerID, DoubleVector weakPredictions) {
    double weight;
    double em = lambda_wrong[learnerID] / (lambda_correct[learnerID] + lambda_wrong[learnerID]);
    if ((em == 0.0) || (em > 0.5)) {
      weight = 0.0;
    } else {
      double Bm = em / (1.0 - em);
      weight = Math.log(1.0 / Bm);
    }

    if ((weight > 0.0) ) {
      if (weakPredictions.sumOfValues() > 0) {
        weakPredictions.normalize();
        weakPredictions.scaleValues(weight);
      }
    }
  }

  @Override
  public void update(InstanceContent trainInstance, double[] votes) {
    for(int i = 0; i < votes.length; i++) {
      // Need to artificially update the model index
      // TODO: Prolly don't need to create a copy here
      InstanceContent instanceWithModelIndex = new InstanceContent(trainInstance.getInstanceIndex(),
          trainInstance.getInstance(), trainInstance.isTraining(), trainInstance.isTesting());
      instanceWithModelIndex.setClassifierIndex(i);
      incrementalUpdate(instanceWithModelIndex, (int) votes[i]);
    }
  }

  /**
   * Incrementally updates the model given the prediction of specific classifier, and the training instance.
   *
   * Note that we are assuming that the trainInstance has been annotated with the correct classifier
   * index.
   * This function should end up in the interface as well, if possible.
   * @param trainInstance An instance, with features, label and classifier index
   * @param prediction The predicted class index of a specific weak learner for the instance.
   */
  public void incrementalUpdate(InstanceContent trainInstance, int prediction) {
    int modelIndex = trainInstance.getClassifierIndex();
    if ((int) trainInstance.getInstance().classValue() == prediction) { // TODO: How do we know if classValue and prediction index match?
      lambda_correct[modelIndex] += lambda;
      lambda *= trainingWeightSeenByModel / (2 * lambda_correct[modelIndex]);
    } else {
      lambda_wrong[modelIndex] += lambda;
      lambda *= trainingWeightSeenByModel / (2 * lambda_wrong[modelIndex]);
    }
  }

  @Override
  public void updateModelAndWeak(InstanceContent instanceContent, LocalLearner weakLearner) {
    // This is a crude way to estimate the weight seen by the model. If an instance has made it this far, we have
    // trained the boosting model with it. Assuming all instances are incrementally indexed, their index will
    // be the weight the model has seen so far TODO: Will prolly have to fix this at the interface level.
    if (instanceContent.getInstanceIndex() < 0 ) {
      return;
    }
    trainingWeightSeenByModel = instanceContent.getInstanceIndex();
    int k = MiscUtils.poisson(lambda, random); //set k according to poisson
    Instance trainInstance = instanceContent.getInstance();
    if (k > 0) {
      trainInstance.setWeight(trainInstance.weight() * k);
      weakLearner.trainOnInstance(trainInstance);
    }
    // Get prediction for the instance from the specific learner of the ensemble
    double[] votes = weakLearner.getVotesForInstance(trainInstance);
    incrementalUpdate(instanceContent,  maxIndex(votes));
    if (instanceContent.getClassifierIndex() == ensembleSize - 1) { // If this is the last classifier in the pipeline
      // Reset the lambda to 1.0
      lambda = 1.0;
    }
  }
}
