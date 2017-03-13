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

public class ModelProcessor implements Processor {
  private Stream outputStream;
  private Stream modelUpdateStream;

  @Override
  public boolean process(ContentEvent event) {
    return false;
  }

  @Override
  public void onCreate(int id) {

  }

  @Override
  public Processor newProcessor(Processor processor) {
    return null;
  }

  public Stream getModelUpdateStream() {
    return modelUpdateStream;
  }

  public Stream getOutputStream() {
    return outputStream;
  }

  public void setOutputStream(Stream outputStream) {
    this.outputStream = outputStream;
  }
}
