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
  private BoostingModel boostingModel;
  private Stream learnerStream;
  private Stream outputStream;

  public BoostModelProcessor(BoostingModel boostingModel) {
    this.boostingModel = boostingModel;
  }

  private BoostModelProcessor(BoostModelProcessor oldProcessor) {
    this.boostingModel = oldProcessor.boostingModel;
    this.outputStream = oldProcessor.outputStream;
    this.learnerStream = oldProcessor.learnerStream;
  }

  @Override
  /**
   * For InstanceContentEvent's coming from the source, attach the boosting model and move forward.
   * For BoostContentEvent coming from the last learner, update the model as needed.
   */
  public boolean process(ContentEvent event) {

    if (event instanceof InstanceContentEvent) {
      learnerStream.put(new BoostContentEvent((InstanceContentEvent) event, boostingModel));
    } else { // Then we must have an BoostContentEvent
      // So we update the model
      // TODO: Does the model update happen here? Or is the model we receive already updated?
      BoostContentEvent boostContentEvent = (BoostContentEvent) event;
      this.boostingModel = boostContentEvent.getBoostingModel();
      outputStream.put(boostContentEvent.getInstanceContentEvent());
    }

    return false;
  }

  @Override
  public void onCreate(int id) {

  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    BoostModelProcessor oldModelProcessor = (BoostModelProcessor) oldProcessor;
    return new BoostModelProcessor(oldModelProcessor);
  }



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
