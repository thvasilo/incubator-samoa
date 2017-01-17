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

package org.apache.samoa.learners.classifiers.ensemble;

import com.google.common.base.Preconditions;
import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.instances.Instance;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;

/**
 * The Class BoostingDistributorProcessor.
 */
public class GradientBoostingDistributorProcessor extends BaggingDistributorProcessor {
  
  /**
   * The ensemble size.
   */
  private int ensembleSize;
  
  /**
   * The testing streams of the ensemble.
   */
  private Stream[] ensembleStreams;
  
  
  @Override
  protected void train(InstanceContentEvent inEvent) {
    // Boosting is trained from the prediction combiner, not from the input
  }
  
  /**
   * On event.
   *
   * @param event the event
   * @return true, if successful
   */
  @Override
  public boolean process(ContentEvent event) {
    Preconditions.checkState(ensembleSize == ensembleStreams.length, String.format(
            "Ensemble size ({}) and number of testing streams ({}) do not match.",
            ensembleSize, ensembleStreams.length));
    InstanceContentEvent inEvent = (InstanceContentEvent) event;
    
    //what is the use of this block code?
    if (inEvent.getInstanceIndex() < 0) {
      // end learning
      for (Stream stream : ensembleStreams)
        stream.put(event);
      return false;
    }
    
    //if is it testing should pass each classifier serially?
    if (inEvent.isTesting()) {
      Instance testInstance = inEvent.getInstance();
      for (int i = 0; i < ensembleSize; i++) {
        Instance instanceCopy = testInstance.copy();
        InstanceContentEvent instanceContentEvent = new InstanceContentEvent(inEvent
                .getInstanceIndex(), instanceCopy, false, true);
//                instanceContentEvent.setClassifierIndex(i); //TODO probably not needed anymore
//                instanceContentEvent.setEvaluationIndex(inEvent.getEvaluationIndex()); //TODO probably not needed anymore
        ensembleStreams[i].put(instanceContentEvent);
      }
    }
    
    // send event to the next ensemble member.
    if (inEvent.isTraining()) {
      int ensembleMember = inEvent.getClassifierIndex();
      if (ensembleMember < 0) { //todo: check
        ensembleStreams[0].put(inEvent);
      } else if (ensembleMember < ensembleSize) {
        ensembleStreams[ensembleMember + 1].put(inEvent); //send to next
      }
      train(inEvent);
    }
    return true;
  }
  
}
