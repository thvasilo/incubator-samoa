package org.apache.samoa.learners.classifiers.ensemble.boosting.pipeline;
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
import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.classifiers.LocalLearner;

import java.io.Serializable;

/**
 * Interface for boosting models.
 * The purpose is to maintain the state of a boosting model so that we can can include
 * it in events that pass from learner to learner, update them as necessary, and make
 * predictions.
 *
 * The prediction part could be moved out of the interface, and the update should be
 * done incrementally (from learner to learner) if possible.
 */
public interface BoostingModel extends Serializable{
  /**
   * Outputs a boosting prediction for the given instance and sum of  weak learner predictions.
   *
   * Should only be called once votes from all predictors have been gathered for an instance.
   * Should probably enforce this in code (take BoostContentEvent as input, maintain count there)
   * @param instance An instance content object, containing the features
   * @param weakPredictionsSum A vector of weak learner predictions that gets aggregated over the boosting pipeline
   * @return An array of doubles, containing the prediction of the boosting model
   */
  double[] predict(InstanceContent instance, DoubleVector weakPredictionsSum);

  /**
   * Adjusts the weight of the prediction vector for a weak learner in place
   * @param learnerID The id of the weak learner in the ensemble
   * @param weakPredictions A vector of predictions produced by the weak learner
   */
  void weighPredictions(int learnerID, DoubleVector weakPredictions);

  /**
   * Given the votes of every weak learner for an instance, and the instance itself, update
   * the boosting model.
   * @param trainInstance A instance containing the features and true dependent
   * @param votes The prediction of each learner in the ensemble for the instance.
   */
  void update(InstanceContent trainInstance, double[] votes);

  /**
   * Takes an instance and a weak learner and incrementally updates the weak learner and boosting model.
   *
   * After studying the interfaces of the different boosting algorithms a bit more, it seems like we need
   * access to the weak learners at each step in order to properly update the model.
   * This should definitely work for OzaBoost, we'll see if it fits the other models as well.
   * @param trainInstance A instance containing the features and true dependent
   * @param weakLearner A reference to the weak learner used for the current iteration of boosting.
   */
  void updateModelAndWeak(InstanceContent trainInstance, LocalLearner weakLearner);

  /**
   * Creates a deep copy of the model and returns it
   * @return A deep copy of the boosting model
   */
  BoostingModel createCopy();
}
