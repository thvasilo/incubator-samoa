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

import org.apache.commons.collections.ArrayStack;
import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.ResultContentEvent;
import org.apache.samoa.moa.core.DoubleVector;
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Stack;

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

  private static final Logger logger =
      LoggerFactory.getLogger(BoostModelProcessor.class);
  private static final long serialVersionUID = -5393272885824859630L;
  // The boosting model maintains the state of the boosting model we use
  private BoostingModel boostingModel;
  // The learner stream connects this processor to the first learner in the boosting pipeline
  private Stream learnerStream;
  // The output stream is the final output of the boosting model, taking the predictions from the boosting pipeline
  // and propagating them down the line (e.g. to the evaluator)
  private Stream outputStream;
  private int ensembleSize;
  // Some debugging metrics
  private long inputsSinceOutput = 0;
  private long totalOutputs = 0;
  private long totalInputs = 0;
  // Used to track the index of the last instance we fully processed
  private long lastProcessedInstanceIndex = 0;
  // Buffer to hold incoming events until all the preceding ones have been fully processed
  private ArrayDeque<InstanceContentEvent> instanceBuffer;

  public BoostModelProcessor(BoostingModel boostingModel, int ensembleSize) {
    this.boostingModel = boostingModel;
    this.ensembleSize = ensembleSize;
    this.instanceBuffer = new ArrayDeque<>();
  }

  // Copy constructor. Note that these are all shallow copies
  private BoostModelProcessor(BoostModelProcessor oldProcessor) {
    this.boostingModel = oldProcessor.boostingModel;
    this.outputStream = oldProcessor.outputStream;
    this.learnerStream = oldProcessor.learnerStream;
    this.ensembleSize = oldProcessor.ensembleSize;
    this.instanceBuffer = oldProcessor.instanceBuffer;
  }

  /**
   * For InstanceContentEvent's coming from the source, attach the boosting model and move forward.
   * For BoostContentEvent coming from the last learner, update the model, and put prediction on the output stream.
   */
  @Override
  public boolean process(ContentEvent event) {

    if (event instanceof InstanceContentEvent) { // Receive an instance event from the source
      inputsSinceOutput++;
      totalInputs++;
      // We augment the source event with the most up-to-date version of the model we have, and push it into the
      // boosting pipeline
      InstanceContentEvent inEvent = (InstanceContentEvent) event;
      int numClasses = inEvent.getInstanceContent().getInstance().numClasses();

      if (inEvent.getInstanceIndex() < 0) { // end learning
        // Check if we still have events waiting to be processed, and flush them
        if (!instanceBuffer.isEmpty()) {
          logger.error("Number of events left over at end: " + instanceBuffer.size());
          // Flush the remaining events in the stream and pray for the best :P
          for (InstanceContentEvent leftoverEvent : instanceBuffer) {
            learnerStream.put(new BoostContentEvent(
                leftoverEvent, boostingModel, new DoubleVector(new double[numClasses])));
          }
        }
        logger.info("Total inputs at end: " + totalInputs);

        learnerStream.put(new BoostContentEvent(
            (InstanceContentEvent) event, boostingModel, new DoubleVector(new double[numClasses])));
        return true;
      }

      // If the incoming event is further ahead from the last instance we fully processed
      if (inEvent.getInstanceIndex() >  lastProcessedInstanceIndex + 1) {
        // Put the event in a buffer until all the previous events are processed
        instanceBuffer.add(inEvent);

        // Check if it's now possible to process the oldest event in the buffer
        if (instanceBuffer.peek().getInstanceIndex() == lastProcessedInstanceIndex + 1) {
          // If we've processed everything until the head of the queue, pop it and send it on.
          learnerStream.put(new BoostContentEvent(
              instanceBuffer.pop(), boostingModel, new DoubleVector(new double[numClasses])));
          }
      } else  { // Else the current event can be processed immediately
        learnerStream.put(new BoostContentEvent(
            (InstanceContentEvent) event, boostingModel, new DoubleVector(new double[numClasses])));
      }
      return true;
    } else { // Then we must have an BoostContentEvent
      totalOutputs++;
      if (totalInputs % 1000 == 0) {
        logger.debug("Inputs since last output: " + inputsSinceOutput);
        logger.debug("Total inputs: " + totalInputs);
        logger.debug("Total outputs: " + totalOutputs);
        inputsSinceOutput = 0;
      }
      BoostContentEvent boostContentEvent = (BoostContentEvent) event;
      InstanceContentEvent inEvent = boostContentEvent.getInstanceContentEvent();
      Instance instance = inEvent.getInstance();
      if (inEvent.getInstanceIndex() < 0) {

        // end learning
        ResultContentEvent outContentEvent = new ResultContentEvent(-1, instance, 0,
            new double[0], true);
        outputStream.put(outContentEvent);
        return false;
      }

      // Update the indicator of the last instance that was fully processed
      lastProcessedInstanceIndex = inEvent.getInstanceIndex();
      // So we update the model
      boostingModel = boostContentEvent.getBoostingModel();
      // Get the aggregated predictions
      DoubleVector predictionsSum = boostContentEvent.getPredictionSum();
      // Create a result event and push it to the output stream.
      ResultContentEvent outContentEvent = new ResultContentEvent(inEvent.getInstanceIndex(),
          instance, inEvent.getClassId(), predictionsSum.getArrayRef(), inEvent.isLastEvent());
      outputStream.put(outContentEvent);
      return true;
    }
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
