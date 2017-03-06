package org.apache.samoa.learners.classifiers.ensemble.boosting;

import com.google.common.collect.ImmutableSet;
import org.apache.samoa.core.Processor;
import org.apache.samoa.instances.Instances;
import org.apache.samoa.learners.ClassificationLearner;
import org.apache.samoa.learners.classifiers.SimpleClassifierAdapter;
import org.apache.samoa.moa.classifiers.functions.MajorityClass;
import org.apache.samoa.moa.classifiers.trees.DecisionStump;
import org.apache.samoa.moa.classifiers.trees.HoeffdingTree;
import org.apache.samoa.topology.Stream;
import org.apache.samoa.topology.TopologyBuilder;

import java.util.Set;

/**
 * BoostLocal is an implementation of boosting aimed at data parallelism.
 *
 * The topology that we use currently is this:
 *
 * The input data is fed into a {@link BoostModelProcessor} object, which augments it with the boosting
 * model an passes it on to the learner ensemble.
 * The learner ensemble is a number of {@link BoostLocalProcessor} objects, each holds the state of a weak learner
 * that is trained locally. One output of {@link BoostModelProcessor} is connected to the first
 * {@link BoostLocalProcessor} in the pipeline.
 * The ensemble is connected in a pipeline manner, each learner connected to the next via a stream.
 * The augmented instance (instance + boosting model) passes through each weak learner in sequence, updating its
 * weak learner, and boosting model at each step, and at some point exits the pipeline.
 * The instance is the returned to the {@link BoostLocalProcessor}, which updates its copy of the boosting model,
 * and outputs the result that came from the last {@link BoostLocalProcessor} in the pipeline.
 *
 * As it stands now, the {@link BoostModelProcessor} continues sending in new data instances as they arrive, potentially
 * attaching an "outdated" model to them, until the previous instance has passed through the pipeline and the boosting
 * model is updated at the processor.
 */
public class BoostLocal implements ClassificationLearner {

  private static final long serialVersionUID = -1853481790434442050L;

  private BoostModelProcessor boostModelProcessor;

  @Override
  public void init(TopologyBuilder topologyBuilder, Instances dataset, int parallelism) {

    final int ensembleSize = 5;
    // Allocate an array for the local processors
    BoostLocalProcessor[] localEnsemble = new BoostLocalProcessor[ensembleSize];
    // Instantiate the model processor and add it to the topology
    boostModelProcessor = new BoostModelProcessor(new OzaBoost(ensembleSize), ensembleSize);
    topologyBuilder.addProcessor(boostModelProcessor);

    // Instantiate learner processors, and add them to the topology
    for (int i = 0; i < ensembleSize; i++) {
      // TODO: Question - Should I be instantiating the LocalLearner objects here, or do it in the constructor of
      // of the BoostLocalProcessor? Given how Java handles objects, a reference to the localLearner object
      // is passed to the constructor of BoostLocalProcessor. Does this get recreated through the newProcessor
      // method of BoostLocalProcessor when the topology is instantiated?
      SimpleClassifierAdapter localLearner = new SimpleClassifierAdapter(new DecisionStump(), dataset);
      // We add a local learner instance and the ensemble id to the local processor
      BoostLocalProcessor boostLocalProcessor = new BoostLocalProcessor(i, ensembleSize, localLearner);
      // TODO: Does this way of creating the processors actually provide any parallelism?
      topologyBuilder.addProcessor(boostLocalProcessor);
      localEnsemble[i] = boostLocalProcessor;
    }

    // Connect the model processor to the first learner
    Stream learnerStream = topologyBuilder.createStream(boostModelProcessor);
    topologyBuilder.connectInputShuffleStream(learnerStream, localEnsemble[0]);
    boostModelProcessor.setLearnerStream(learnerStream);

    // These streams move events from learner to learner
    Stream[] ensembleStreams = new Stream[ensembleSize];
    // Instantiate the output streams for every learner
    for (int i = 0; i < ensembleSize; i++) {
      ensembleStreams[i] = topologyBuilder.createStream(localEnsemble[i]);
      localEnsemble[i].setOutputStream(ensembleStreams[i]);
    }
    // Connect each learner to the next
    for (int i = 1; i < ensembleSize; i++) {
      // Connect the output of the previous to the current learner
      topologyBuilder.connectInputKeyStream(localEnsemble[i - 1].getOutputStream(), localEnsemble[i]);
    }
    // Connect the last learner to the model processor, which handles the final output.
    topologyBuilder.connectInputShuffleStream(localEnsemble[ensembleSize - 1].getOutputStream(), boostModelProcessor);

    // Create the output stream from the model processor
    Stream outputStream = topologyBuilder.createStream(boostModelProcessor);
    boostModelProcessor.setOutputStream(outputStream);
  }

  @Override
  public Processor getInputProcessor() {
    // Connect the {@link BoostModelProcessor} to the input data
    return boostModelProcessor;
  }

  @Override
  public Set<Stream> getResultStreams() {
    // Have the output of the {@link BoostModelProcessor} as the learner output
    return ImmutableSet.of(boostModelProcessor.getOutputStream());
  }
}
