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

import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.InstanceContentEvent;
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
  private static final Random random = new Random();
  private final int ensembleSize;
  private double[] lambda_wrong;
  private double[] lambda_correct;
  private double[] epsilon;
  private double lambda;

  public OzaBoost(int ensembleSize) {
    this.ensembleSize = ensembleSize;
    lambda = 1.0;
    lambda_correct = new double[ensembleSize];
    lambda_wrong = new double[ensembleSize];
    epsilon = new double[ensembleSize];
  }

  @Override
  public double[] predict(InstanceContent instance) {
    return new double[0];
  }

  @Override
  public void update(InstanceContent trainInstance, double[] votes) {
    for(int i = 0; i < votes.length; i++) {
      // Need to artificially update the model index
      // TODO: Prolly don't need to create a copy here
      InstanceContent instanceWithModelIndex = new InstanceContent(trainInstance.getInstanceIndex(),
          trainInstance.getInstance(), trainInstance.isTraining(), trainInstance.isTesting());
      instanceWithModelIndex.setClassifierIndex(i);
      incrementalUpdate(instanceWithModelIndex, votes[i]);
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
  public void incrementalUpdate(InstanceContent trainInstance, double prediction) {
    int modelIndex = trainInstance.getClassifierIndex();
    if (trainInstance.getInstance().classValue() == prediction) { // TODO: How do we know if classValue and prediction index match?
      lambda_correct[modelIndex] += lambda;
      epsilon[modelIndex] =
          lambda_correct[modelIndex] / (lambda_correct[modelIndex] + lambda_wrong[modelIndex]);
      lambda *= 1 / (2 * (1 - epsilon[modelIndex]));
    } else {
      lambda_wrong[modelIndex] += lambda;
      // TODO: The epsilon updates are the same for both cases, why?
      epsilon[modelIndex] =
          lambda_correct[modelIndex] / (lambda_correct[modelIndex] + lambda_wrong[modelIndex]);
      lambda *= 1 / (2 * epsilon[modelIndex]);
    }
  }

  @Override
  public void updateWeak(InstanceContent instanceContent, LocalLearner weakLearner) {
    int k = MiscUtils.poisson(lambda, random); //set k according to poisson
    Instance trainInstance = instanceContent.getInstance();
    if (k > 0) {
      trainInstance.setWeight(trainInstance.weight() * k);
      weakLearner.trainOnInstance(trainInstance);
    }
    //get prediction for the instance from the specific learner of the ensemble
    double[] prediction = weakLearner.getVotesForInstance(trainInstance);
    incrementalUpdate(instanceContent,  maxIndex(prediction));
  }
}
