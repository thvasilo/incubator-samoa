package org.apache.samoa.tasks;
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

import com.github.javacliparser.ClassOption;
import com.github.javacliparser.Configurable;
import com.github.javacliparser.IntOption;
import org.apache.samoa.learners.classifiers.ensemble.BoostLocal;
import org.apache.samoa.streams.InstanceStream;
import org.apache.samoa.streams.PrequentialSourceProcessor;
import org.apache.samoa.streams.generators.RandomTreeGenerator;
import org.apache.samoa.topology.ComponentFactory;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.Topology;
import org.apache.samoa.topology.TopologyBuilder;

public class MyTask implements Task, Configurable {
  private static final long serialVersionUID = -4835156456545828433L;
  /** The topology builder for the task. */
  private TopologyBuilder topologyBuilder;
  /** The topology that will be created for the task */
  private Topology myTopology;

  // Options need to be public so they are visible for the config
  public ClassOption streamTrainOption = new ClassOption("trainStream", 's', "Stream to learn from.",
      InstanceStream.class, RandomTreeGenerator.class.getName());

  public IntOption instanceLimitOption = new IntOption("instanceLimit", 'i',
      "Maximum number of instances to test/train on  (-1 = no limit).", 10, -1,
      Integer.MAX_VALUE);

  @Override
  public void init() {
    // Initialize the input processor
    PrequentialSourceProcessor preqSource = new PrequentialSourceProcessor();
    preqSource.setStreamSource((InstanceStream) streamTrainOption.getValue());
    preqSource.setMaxNumInstances(instanceLimitOption.getValue());
    topologyBuilder.addEntranceProcessor(preqSource);

    // Create stream from the source
    Stream sourcePiOutputStream = topologyBuilder.createStream(preqSource);

    // Instantiate the processor
    BoostLocal boostLocal = new BoostLocal();
    boostLocal.init(topologyBuilder, preqSource.getDataset(), 1);

    // Connect the input stream to the processor
    topologyBuilder.connectInputShuffleStream(sourcePiOutputStream, boostLocal.getInputProcessor());

    myTopology = topologyBuilder.build();
  }

  @Override
  public Topology getTopology() {
    return myTopology;
  }

  @Override
  public void setFactory(ComponentFactory factory) {
    topologyBuilder = new TopologyBuilder(factory);
    topologyBuilder.initTopology("My task");
  }
}
