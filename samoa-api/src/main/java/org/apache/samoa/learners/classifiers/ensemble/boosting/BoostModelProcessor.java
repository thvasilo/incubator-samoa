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
import org.apache.samoa.core.DoubleVector;
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;

/**
 * Handles the updating of the model, and the propagation of the updated model at each super-step
 *
 * The role of the BoostModelProcessor is to get events from the source and augment them with the
 * boosting model before sending them on to the boosting pipeline.
 * It maintains an up-to-date version of the model for each iteration. We assume the model only changes
 * between super-steps, i.e. it remains static while an instance makes its way through the boosting
 * pipeline.
 */
public class BoostModelProcessor implements Processor {

  private static final long serialVersionUID = -5393272885824859630L;
  // The boosting model maintains the state of the boosting model we use
  private BoostingModel boostingModel;
  // The learner stream connects this processor to the first learner in the boosting pipeline
  private Stream learnerStream;
  // The output stream is the final output of the boosting model, taking the predictions from the boosting pipeline
  // and propagating them down the line (e.g. to the evaluator)
  private Stream outputStream;
  private int ensembleSize;

  public BoostModelProcessor(BoostingModel boostingModel, int ensembleSize) {
    this.boostingModel = boostingModel;
    this.ensembleSize = ensembleSize;
  }

  // Copy constructor. Note that these are all shallow copies
  private BoostModelProcessor(BoostModelProcessor oldProcessor) {
    this.boostingModel = oldProcessor.boostingModel;
    this.outputStream = oldProcessor.outputStream;
    this.learnerStream = oldProcessor.learnerStream;
    this.ensembleSize = oldProcessor.ensembleSize;
  }

  @Override
  /**
   * For InstanceContentEvent's coming from the source, attach the boosting model and move forward.
   * For BoostContentEvent coming from the last learner, update the model as needed.
   */
  public boolean process(ContentEvent event) {

    if (event instanceof InstanceContentEvent) { // Receive an instance event from the source
      // We augment the source event with the most up-to-date version of the model we have, and push it into the
      // boosting pipeline
      // TODO: Here is where we might have to stall until the model update comes in from the last instance event
      learnerStream.put(new BoostContentEvent(
          (InstanceContentEvent) event, boostingModel, new DoubleVector(new double[ensembleSize])));
    } else { // Then we must have an BoostContentEvent
      // So we update the model
      BoostContentEvent boostContentEvent = (BoostContentEvent) event;
      this.boostingModel = boostContentEvent.getBoostingModel();
      // And push the event to the output.
      // TODO: Will need to create a ResultContentEvent here, based on the prediction of the boosting model.
      outputStream.put(boostContentEvent.getInstanceContentEvent());
    }

    return false; // TODO: When should we return true and when false?
  }

  @Override
  public void onCreate(int id) {

  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    BoostModelProcessor oldModelProcessor = (BoostModelProcessor) oldProcessor;
    return new BoostModelProcessor(oldModelProcessor);
  }

  // TODO: Will need to verify whether these can be set in constructor instead of setter functions, making the streams final
  public void setLearnerStream(Stream learnerStream) {
    this.learnerStream = learnerStream;
  }

  public void setOutputStream(Stream outputStream) {
    this.outputStream = outputStream;
  }

  public Stream getOutputStream() {
    return outputStream;
  }
}
