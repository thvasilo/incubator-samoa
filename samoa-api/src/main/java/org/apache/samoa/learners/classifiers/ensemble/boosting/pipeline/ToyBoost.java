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

public class ToyBoost implements BoostingModel {
  private int super_steps = 0;
  private int steps = 0;

  @Override
  public BoostingModel createCopy() {
    ToyBoost copy = new ToyBoost();
    copy.steps = this.steps;
    copy.super_steps = this.super_steps;
    return copy;
  }

  @Override
  public double[] predict(InstanceContent instance, DoubleVector weakPredictionsSum) {
    return new double[0];
  }

  @Override
  public void weighPredictions(int learnerID, DoubleVector weakPredictions) {
    System.out.println("id: " + learnerID + " weakPrediction: " + weakPredictions);
    weakPredictions.scaleValues(learnerID + 1);
  }

  @Override
  public void update(InstanceContent trainInstance, double[] votes) {
    super_steps++;
  }

  @Override
  public void updateModelAndWeak(InstanceContent trainInstance, LocalLearner weakLearner) {
    steps++;
    weakLearner.trainOnInstance(trainInstance.getInstance());
    System.out.println("State of boosting model: " + steps);
  }
}
