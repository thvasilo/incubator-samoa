package org.apache.samoa.learners.classifiers.ensemble.boosting.poca;
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
import org.apache.samoa.topology.Stream;

/**
 * The input processor is quite simple, it just gets the input from the source and broadcasts it
 * to the weak learners.
 */
public class InputProcessor implements Processor {
  private static final long serialVersionUID = 321254207612577237L;

  Stream inputEventStream;

  @Override
  public boolean process(ContentEvent event) {
    inputEventStream.put(event);
    return true;
  }

  @Override
  public void onCreate(int id) {

  }

  @Override
  public Processor newProcessor(Processor processor) {
    return null;
  }

  public Stream getInputEventStream() {
    return inputEventStream;
  }

  public void setInputEventStream(Stream inputEventStream) {
    this.inputEventStream = inputEventStream;
  }
}
