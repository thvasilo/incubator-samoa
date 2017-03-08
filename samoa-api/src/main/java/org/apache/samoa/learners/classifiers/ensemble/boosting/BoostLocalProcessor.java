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

import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContent;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.learners.classifiers.LocalLearner;
import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.topology.Stream;

/**
 * Maintains the state of a local weak learner in the context of a boosting algorithm.
 *
 * Note that the state/prediction of the boosting algorithm is split into parts, and lives in different classes,
 * which is not ideal:
 * From what I've been able to to decipher from the various boosting algos, the prediction always relies on a sum of the
 * weak learner predictions, with different ways to calculate the weight of each weak learner.
 * To achieve that we have delegated the weighting of the prediction to {@link BoostingModel} through the function
 * weighPredictions, which takes the raw predictions of the weak learner and applies proper weighting.
 * In OzaBoost for example this includes the normalization of the values.
 * The summing of the votes and updating the running sum is however the responsibility of {@link BoostLocalProcessor},
 * as the sum lives in the {@link BoostContentEvent} message, and right now is not affected by the {@link BoostingModel}
 * This API could change though, for example we could pass the running sum to the weighPredictions function as well.
 * It's a matter of preference I guess. It would move the responsibility of summing to model from this processor.
 *
 */
public class BoostLocalProcessor implements Processor {

  private static final long serialVersionUID = -8744327519836673493L;
  private final int processorId;
  private final int ensembleSize;

  // This is the local learner instance that we will be training.
  private LocalLearner localLearner;

  // The output stream is directed either at the next learner in the boosting pipeline, or the BoostModelProcessor
  private Stream outputStream;


  public BoostLocalProcessor(int processorId, int ensembleSize, LocalLearner localLearner) {
    this.processorId = processorId;
    this.localLearner = localLearner;
    this.ensembleSize = ensembleSize;
  }


  /**
   * Processes events of type {@link BoostContentEvent}, updating the weak learner, the boosting model, and making
   * predictions.
   * @param event A {@link BoostContentEvent} containing the boosting model and an {@link InstanceContentEvent}
   * @return
   */
  @Override
  public boolean process(ContentEvent event) {
//    System.out.println("id: " + processorId + " event: " + event);

    // Cast and unpack event
    BoostContentEvent boostContentEvent = (BoostContentEvent) event;
    BoostingModel boostingModel = boostContentEvent.getBoostingModel().createCopy();
    InstanceContentEvent inEvent = boostContentEvent.getInstanceContentEvent();
    InstanceContent instanceContent = inEvent.getInstanceContent();
    InstanceContent newInstanceContent = new InstanceContent(instanceContent.getInstanceIndex(),
        instanceContent.getInstance(), instanceContent.isTraining(), instanceContent.isTesting());
    Instance instance = newInstanceContent.getInstance();
    DoubleVector predictionsSum = new DoubleVector(boostContentEvent.getPredictionSum());

    // Update the instance's classifier index, this is used downstream to know which weak learner we are affecting
    newInstanceContent.setClassifierIndex(processorId);

    if (inEvent.isTesting()) {
      // Make the weak learner prediction, weight it according to the boosting model, and add it to the running sum
      double[] votes = localLearner.getVotesForInstance(instance);
      // Scale the votes according to the weak classifier's weight
      boostingModel.weighPredictions(processorId, new DoubleVector(votes));
      // Add the scaled votes to the running sum of class predictions
      predictionsSum.addValues(votes);
    }

    if (inEvent.isTraining()) {
      // Update the weak learner and the boosting model in-place.
      boostingModel.updateModelAndWeak(newInstanceContent, localLearner);
    }

    // No need to create new event, all the event components are modified in-place
    outputStream.put(new BoostContentEvent(inEvent, boostingModel, predictionsSum));
    return true;
  }

  @Override
  public void onCreate(int id) {
    localLearner.resetLearning();
  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    BoostLocalProcessor oldLocalProcessor = (BoostLocalProcessor) oldProcessor;
    BoostLocalProcessor newProcessor = new BoostLocalProcessor(
        oldLocalProcessor.getProcessorId(),
        oldLocalProcessor.getEnsembleSize(),
        oldLocalProcessor.getLocalLearner());
    newProcessor.getLocalLearner().resetLearning();
    newProcessor.setOutputStream(oldLocalProcessor.getOutputStream());
    return newProcessor;
  }

  public void setOutputStream(Stream outputStream) {
    this.outputStream = outputStream;
  }

  public Stream getOutputStream() {
    return outputStream;
  }

  public int getProcessorId() {
    return processorId;
  }

  public int getEnsembleSize() {
    return ensembleSize;
  }

  public LocalLearner getLocalLearner() {
    return localLearner;
  }
}
