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

import org.apache.samoa.core.ContentEvent;
import org.apache.samoa.core.Processor;
import org.apache.samoa.learners.InstanceContentEvent;
import org.apache.samoa.topology.Stream;

public class BoostLocalProcessor implements Processor {

  public BoostLocalProcessor(int processorId) {
    this.processorId = processorId;
  }

  private static final long serialVersionUID = -8744327519836673493L;
  private int processorId;
  Stream inputStream;
  Stream outputStream;

  @Override
  public boolean process(ContentEvent event) {
    System.out.println("id: " + processorId + " event: " + event);
    return true;
  }

  @Override
  public void onCreate(int id) {
    processorId = id;
  }

  @Override
  public Processor newProcessor(Processor oldProcessor) {
    BoostLocalProcessor oldLocalProcessor = (BoostLocalProcessor) oldProcessor;
    BoostLocalProcessor newProcessor = new BoostLocalProcessor(oldLocalProcessor.getProcessorId());
    return newProcessor;
  }

  public Stream getInputStream() {
    return inputStream;
  }

  public Stream getOutputStream() {
    return outputStream;
  }

  public int getProcessorId() {
    return processorId;
  }
}
